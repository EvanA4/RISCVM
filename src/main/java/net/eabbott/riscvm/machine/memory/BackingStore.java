package net.eabbott.riscvm.machine.memory;

public interface BackingStore {
    byte[] read(long address, int size);
    void write(long address, byte[] data);
}
