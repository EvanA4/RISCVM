package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.context.Context;

package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.Hart;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryManager implements BackingStore {
    public static final ConcurrentMap<Integer, Cache> L1_CACHES =
            new ConcurrentHashMap<>();
    public static final ConcurrentMap<Integer, Cache> L2_CACHES =
            new ConcurrentHashMap<>();

    public final RAM ram;
    public final Hart hart;

    private final Cache l1;

    public MemoryManager(Hart hart, RAM sharedRam, L3 sharedL3, Context context) {
        this.hart = hart;
        this.ram = sharedRam;

        Cache l2 = new Cache(context.l2Cache, sharedL3);
        this.l1 = new Cache(context.l1Cache, l2);

        Cache previousL1 = L1_CACHES.putIfAbsent(hart.id, l1);
        Cache previousL2 = L2_CACHES.putIfAbsent(hart.id, l2);

        if (previousL1 != null || previousL2 != null) {
            // Roll back any partial registration before reporting the duplicate.
            L1_CACHES.remove(hart.id, l1);
            L2_CACHES.remove(hart.id, l2);
            throw new IllegalStateException(
                    "Caches already registered for hart " + hart.id);
        }
    }

    @Override
    public byte[] read(long address, int size) {
        return l1.read(address, size);
    }

    @Override
    public void write(long address, byte[] data) {
        l1.write(address, data);
    }

    public static Cache getL1(int hartId) {
        return L1_CACHES.get(hartId);
    }

    public static Cache getL2(int hartId) {
        return L2_CACHES.get(hartId);
    }

    public static void unregisterHart(int hartId) {
        L1_CACHES.remove(hartId);
        L2_CACHES.remove(hartId);
    }

    public static void clearCacheRegistry() {
        L1_CACHES.clear();
        L2_CACHES.clear();
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
*   - Translate, verifying permissions
* - Look up in local L1 cache
*   - Caches map from memory block address to the block data
*   - Use fancy MESI protocol
*   - SNOOP vs. DIR = whether they track who has shared values or if you just broadcast
*   - MESI is a write-invalidate protocol
*   - Hardcode satp to be 0 if MMU is disabled by command line
* */