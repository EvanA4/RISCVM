package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.memory.cache.MemoryResult;

/**
 * The most generalized definition of a backing store. Allows for swapping out backing stores as necessary.
 */
public interface BackingStore {
    MemoryResult read(long address, int size);
    MemoryResult write(long address, byte[] data);
}
