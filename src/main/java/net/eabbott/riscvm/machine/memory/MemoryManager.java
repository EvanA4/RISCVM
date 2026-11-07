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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntUnaryOperator;

public final class MemoryManager {
    private static final int STRIPE_COUNT = 256; // Must be power of 2
    private static final Object[] STRIPES = new Object[STRIPE_COUNT];

    // Populate stripe lock array
    static {
        Arrays.setAll(STRIPES, ignored -> new Object());
    }

    // LR reservations, one per hart
    private static final ConcurrentMap<Integer, Long> RESERVATIONS =
        new ConcurrentHashMap<>();

    // Get stripe lock from physical address, based on SplitMix64
    // https://rosettacode.org/wiki/Pseudo-random_numbers/Splitmix64
    private static Object getStripe(long physicalAddress) {
        long x = physicalAddress;
        x += 0x9e3779b97f4a7c15L;
        x = (x ^ (x >> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >> 27)) * 0x94d049bb133111ebL;
        x ^= (x >> 31);
        return STRIPES[(int) x & (STRIPE_COUNT - 1)];
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

        synchronized (getStripe(address)) {
            return top.read(address, size);
        }
    }

    public MemoryResult write(long address, byte[] data, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, data.length, accessType);

        synchronized (getStripe(address)) {
            invalidateReservations(address);
            return top.write(address, data);
        }
    }

    // Atomic-relevant code below:

    public AMOResult rmw(long address, IntUnaryOperator func) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, 4, AccessType.AMO);

        synchronized (getStripe(address)) {
            MemoryResult result = top.read(address, 4);
            if (result.shouldWait()) return new AMOResult(true, 0);

            int oldValue = BinaryUtil.intFromBytesLE(result.data(), 0, 4);
            invalidateReservations(address);
            top.write(address, BinaryUtil.bytesFromIntLE(func.applyAsInt(oldValue)));
            return new AMOResult(false, oldValue);
        }
    }

    public AMOResult loadReserved(long address) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        synchronized (getStripe(physicalAddress)) {
            MemoryResult result = top.read(physicalAddress, 4);
            if (result.shouldWait()) return new AMOResult(true, 0);

            int value = BinaryUtil.intFromBytesLE(result.data(), 0, 4);
            RESERVATIONS.put(hart.id, physicalAddress);
            return new AMOResult(false, value);
        }
    }

    /**
     * Attempts an SC.W. Returns 0 on success and 1 on failure.
     */
    public AMOResult storeConditional(long address, int value) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        synchronized (getStripe(physicalAddress)) {
            Long reservation = RESERVATIONS.remove(hart.id);

            if (reservation == null || reservation != physicalAddress) {
                return new AMOResult(false, 1);
            }

            invalidateReservations(physicalAddress);
            // This write MUST go through, so unfortunately this must be looped
            MemoryResult result = top.write(physicalAddress, BinaryUtil.bytesFromIntLE(value));
            while (result.shouldWait()) {
                result = top.write(physicalAddress, BinaryUtil.bytesFromIntLE(value));
            }
            return new AMOResult(false, 0);
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
    private static void invalidateReservations(long physicalAddress) {
        for (Map.Entry<Integer, Long> entry : RESERVATIONS.entrySet()) {
            if (entry.getValue() == physicalAddress) {
                RESERVATIONS.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
