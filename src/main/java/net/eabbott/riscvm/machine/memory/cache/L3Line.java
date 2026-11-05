package net.eabbott.riscvm.machine.memory.cache;

public class L3Line {
    final long blockAddress;
    final byte[] data;
    boolean dirty;
    long frequency;
    final long loadedAt;
    long lastUsed;

    L3Line(long blockAddress, byte[] data, long timestamp) {
        this.blockAddress = blockAddress;
        this.data = data;
        this.dirty = false;
        this.frequency = 1;
        this.loadedAt = timestamp;
        this.lastUsed = timestamp;
    }
}