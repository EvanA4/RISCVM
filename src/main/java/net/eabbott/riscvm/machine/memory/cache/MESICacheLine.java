package net.eabbott.riscvm.machine.memory.cache;

public class MESICacheLine {
    final long blockAddress;
    final byte[] data;
    MESI state;
    long frequency;
    final long loadedAt;
    long lastUsed;

    MESICacheLine(long blockAddress, byte[] data, MESI state, long timestamp) {
        this.blockAddress = blockAddress;
        this.data = data;
        this.state = state;
        this.frequency = 1;
        this.loadedAt = timestamp;
        this.lastUsed = timestamp;
    }
}