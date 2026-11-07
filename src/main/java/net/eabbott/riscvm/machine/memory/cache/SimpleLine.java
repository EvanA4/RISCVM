package net.eabbott.riscvm.machine.memory.cache;

public class SimpleLine extends AbstractCacheLine {
    boolean dirty;

    SimpleLine(long blockAddress, byte[] data, long timestamp) {
        super(blockAddress, data, timestamp);
        this.dirty = false;
    }
}