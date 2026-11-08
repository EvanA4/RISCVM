package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.memory.cache.L2Cache;
import net.eabbott.riscvm.machine.memory.cache.MemoryResult;
import net.eabbott.riscvm.machine.register.CSRFile;
import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.memory.cache.L1Cache;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.memory.virtual.MMU;
import net.eabbott.riscvm.util.BinaryUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;

/**
 * This stupid memory seam is probably the hardest programming challenge I've ever had.
 * If I get points docked for a missing requirement, I will NOT do anything illegal.
 * I will be very upset, though.
 */
public final class MemoryManager {
    private static final int STRIPE_SHIFT = 2;
    private static final int STRIPE_COUNT = 256;
    private static final ReentrantLock[] STRIPES = new ReentrantLock[STRIPE_COUNT];

    // Populate stripe lock array
    static {
        Arrays.setAll(STRIPES, ignored -> new ReentrantLock());
    }

    // LR reservations, one per hart
    private static final ConcurrentMap<Integer, Long> RESERVATIONS =
        new ConcurrentHashMap<>();

    // Get stripe lock from physical address
    private static int stripeIndex(long address) {
        return (int) ((address >>> STRIPE_SHIFT) & (STRIPE_COUNT - 1));
    }

    // Fetch stripe lock indices for a memory operation
    private static SortedSet<Integer> stripesForRange(long address, int size) {
        SortedSet<Integer> indices = new TreeSet<>();
        for (int i = 0; i < size; ++i) {
            indices.add(stripeIndex(address + i));
        }
        return indices;
    }

    // Acquires the appropriate locks in a sorted order to avoid deadlocks
    private void acquireStripes(SortedSet<Integer> indices) {
        for (Integer i : indices) STRIPES[i].lock();
    }

    // Acquires the appropriate locks in a sorted order to avoid deadlocks
    private void releaseStripes(SortedSet<Integer> indices) {
        for (Integer i : indices.reversed()) STRIPES[i].unlock();
    }

    public final MMU mmu;
    public final RAM ram;
    public final Hart hart;
    public final BackingStore top;

    public MemoryManager(Hart hart, Context context) {
        VirtualMachine machine = VirtualMachine.getInstance();

        this.hart = hart;
        this.mmu = new MMU(hart);
        this.ram = machine.ram;

        boolean shouldHaveL1 = context.cacheDepth >= 1 && context.l1Cache.size != 0;
        boolean shouldHaveL2 = context.cacheDepth >= 2 && context.l2Cache.size != 0;
        boolean shouldHaveL3 = context.cacheDepth >= 3 && context.l3Cache.size != 0;

        BackingStore l2Source = shouldHaveL3 ? machine.l3 : machine.ram;
        L2Cache l2 = new L2Cache(context.cacheCoherency, context.l2Cache, hart.id, l2Source);
        BackingStore l1Source = shouldHaveL2 ? l2 : l2Source;
        L1Cache l1 = new L1Cache(context.cacheCoherency, context.l1Cache, hart.id, l1Source);

        if (shouldHaveL1) this.top = l1;
        else if (shouldHaveL2) this.top = l2;
        else if (shouldHaveL3) this.top = machine.l3;
        else this.top = machine.ram;
    }

    public MemoryResult read(long address, int size, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, size, accessType);

        SortedSet<Integer> relevant = stripesForRange(address, size);
        acquireStripes(relevant);
        MemoryResult output;
        try {
            output = top.read(address, size);
        } finally {
            releaseStripes(relevant);
        }
        return output;
    }

    public MemoryResult write(long address, byte[] data, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, data.length, accessType);

        SortedSet<Integer> relevant = stripesForRange(address, data.length);
        acquireStripes(relevant);
        MemoryResult output;
        try {
            output = top.write(address, data);
            if (!output.shouldWait()) invalidateReservations(address, data.length);
        } finally {
            releaseStripes(relevant);
        }
        return output;
    }

    // Atomic-relevant code below:

    public AMOResult rmw(long address, IntUnaryOperator func) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, 4, AccessType.AMO);
        checkWordAlignment(address);

        SortedSet<Integer> relevant = stripesForRange(address, 4);
        acquireStripes(relevant);

        try {
            MemoryResult result = top.read(address, 4);
            if (result.shouldWait()) {
                return new AMOResult(true, 0);
            }

            int oldValue = BinaryUtil.intFromBytesLE(result.data(), 0, 4);
            int newValue = func.applyAsInt(oldValue);

            result = top.write(address, BinaryUtil.bytesFromIntLE(newValue));
            if (result.shouldWait()) {
                return new AMOResult(true, 0);
            }
            invalidateReservations(address, 4);

            return new AMOResult(false, oldValue);
        } finally {
            releaseStripes(relevant);
        }
    }

    public AMOResult loadReserved(long address) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        SortedSet<Integer> relevant = stripesForRange(physicalAddress, 4);
        acquireStripes(relevant);

        try {
            MemoryResult result = top.read(physicalAddress, 4);
            if (result.shouldWait()) {
                return new AMOResult(true, 0);
            }

            int value = BinaryUtil.intFromBytesLE(result.data(), 0, 4);
            RESERVATIONS.put(hart.id, physicalAddress);

            return new AMOResult(false, value);
        } finally {
            releaseStripes(relevant);
        }
    }

    public AMOResult storeConditional(long address, int value) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        SortedSet<Integer> relevant = stripesForRange(physicalAddress, 4);
        acquireStripes(relevant);

        try {
            Long reservation = RESERVATIONS.get(hart.id);
            if (reservation == null || reservation != physicalAddress) {
                RESERVATIONS.remove(hart.id);
                return new AMOResult(false, 1);
            }

            // Attempt store, abort if stall requested
            MemoryResult result = top.write(physicalAddress, BinaryUtil.bytesFromIntLE(value));
            if (result.shouldWait()) {
                return new AMOResult(true, 0);
            }

            // Only remove reservation(s) after successfully performing store
            invalidateReservations(physicalAddress, 4);

            return new AMOResult(false, 0);

        } finally {
            releaseStripes(relevant);
        }
    }

    private long translateAmoAddress(long address) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0) {
            return this.mmu.translate(address, 4, AccessType.AMO);
        }
        return address;
    }

    private static void checkWordAlignment(long address) {
        if ((address & 0b11) != 0) {
            throw new IllegalArgumentException("Misaligned atomic word access.");
        }
    }

    /**
     * Call while holding the stripe for physicalAddress.
     */
    private static void invalidateReservations(long physicalAddress, int size) {
        long writeEnd = physicalAddress + size;
        for (Map.Entry<Integer, Long> entry : RESERVATIONS.entrySet()) {
            long reservedAddress = entry.getValue();
            long reservationEnd = reservedAddress + 4;

            // If write range overlaps the reservation, invalidate it
            if (physicalAddress < reservationEnd && reservedAddress < writeEnd) {
                RESERVATIONS.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
