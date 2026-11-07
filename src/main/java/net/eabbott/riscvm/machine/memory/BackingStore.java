package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.memory.cache.MemoryResult;

public interface BackingStore {
    MemoryResult read(long address, int size);
    MemoryResult write(long address, byte[] data);
}
