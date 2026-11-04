package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.memory.cache.PrivateCache;
import net.eabbott.riscvm.machine.memory.cache.PublicCache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryManager implements BackingStore {
    public static final ConcurrentMap<Integer, PrivateCache> L1_CACHES =
            new ConcurrentHashMap<>();
    public static final ConcurrentMap<Integer, PrivateCache> L2_CACHES =
            new ConcurrentHashMap<>();

    public final RAM ram;
    public final Hart hart;
    public final PrivateCache l1;

    public MemoryManager(Hart hart, RAM sharedRam, PublicCache sharedL3, Context context) {
        this.hart = hart;
        this.ram = sharedRam;

        PrivateCache l2 = new PrivateCache(context.cacheCoherency, context.l2Cache, hart.id, sharedL3);
        this.l1 = new PrivateCache(context.cacheCoherency, context.l1Cache, hart.id, l2);

        L1_CACHES.put(hart.id, l1);
        L2_CACHES.put(hart.id, l2);
    }

    @Override
    public byte[] read(long address, int size) {
        return l1.read(address, size);
    }

    @Override
    public void write(long address, byte[] data) {
        l1.write(address, data);
    }
}


/*
* Notes:
* ELF file (the user) is responsible for setting up page tables and setting satp to non-zero.
*
* Types of memory requests:
* - Permissions (store = W, load = R, execute = PC)
* - Read/write to memory
* - Virtual memory or not (determined by satp)
* - On scale of byte, halfword, or word
*
* When memory request is made:
* - ...If virtual memory
*   - TODO: Translate, verifying permissions
* - Look up in local L1 cache
*   - Caches map from memory block address to the block data
*   - Use fancy MESI protocol
*   - SNOOP vs. DIR = whether they track who has shared values or if you just broadcast
*   - MESI is a write-invalidate protocol
*   - Hardcode satp to be 0 if MMU is disabled by command line
* */