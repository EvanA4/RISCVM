package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.memory.cache.L2Cache;
import net.eabbott.riscvm.machine.register.CSRFile;
import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.memory.cache.L1Cache;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.memory.virtual.MMU;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryManager {
    public static final ConcurrentMap<Integer, L1Cache> L1_CACHES =
            new ConcurrentHashMap<>();
    public static final ConcurrentMap<Integer, L2Cache> L2_CACHES =
            new ConcurrentHashMap<>();

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
        return l1.read(address, size);
    }

    public void write(long address, byte[] data, AccessType accessType) {
        if (this.hart.csrFile.read(CSRFile.SATP) != 0)
            address = this.mmu.translate(address, data.length, accessType);
        l1.write(address, data);
    }
}
