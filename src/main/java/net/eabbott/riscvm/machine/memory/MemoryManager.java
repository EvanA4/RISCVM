package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.memory.cache.L2Cache;
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
    public static final ConcurrentMap<Integer, L1Cache> L1_CACHES =
            new ConcurrentHashMap<>();
    public static final ConcurrentMap<Integer, L2Cache> L2_CACHES =
            new ConcurrentHashMap<>();

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
    public final L1Cache l1;

    public MemoryManager(Hart hart, Context context) {
        VirtualMachine machine = VirtualMachine.getInstance();

        this.hart = hart;
        this.mmu = new MMU(hart);
        this.ram = machine.ram;

        L2Cache l2 = new L2Cache(context.cacheCoherency, context.l2Cache, hart.id, machine.l3);
        this.l1 = new L1Cache(context.cacheCoherency, context.l1Cache, hart.id, l2);

        L1_CACHES.put(hart.id, l1);
        L2_CACHES.put(hart.id, l2);
    }

    public byte[] read(long address, int size, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, size, accessType);

        synchronized (getStripe(address)) {
            return l1.read(address, size);
        }
    }

    public void write(long address, byte[] data, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, data.length, accessType);

        synchronized (getStripe(address)) {
            invalidateReservations(address);
            l1.write(address, data);
        }
    }

    // Atomic-relevant code below:

    public int rmw(long address, IntUnaryOperator func) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, 4, AccessType.AMO);

        synchronized (getStripe(address)) {
            int oldValue = BinaryUtil.intFromBytesLE(l1.read(address, 4), 0, 4);
            invalidateReservations(address);
            l1.write(address, BinaryUtil.bytesFromIntLE(func.applyAsInt(oldValue)));
            return oldValue;
        }
    }

    public int loadReserved(long address) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        synchronized (getStripe(physicalAddress)) {
            int value = BinaryUtil.intFromBytesLE(l1.read(physicalAddress, 4), 0, 4);
            RESERVATIONS.put(hart.id, physicalAddress);
            return value;
        }
    }

    /**
     * Attempts an SC.W. Returns 0 on success and 1 on failure.
     */
    public int storeConditional(long address, int value) {
        long physicalAddress = translateAmoAddress(address);
        checkWordAlignment(physicalAddress);

        synchronized (getStripe(physicalAddress)) {
            Long reservation = RESERVATIONS.remove(hart.id);

            if (reservation == null || reservation.longValue() != physicalAddress) {
                return 1;
            }

            invalidateReservations(physicalAddress);
            l1.write(physicalAddress, BinaryUtil.bytesFromIntLE(value));
            return 0;
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
