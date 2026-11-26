package net.eabbott.riscvm.machine.memory.cache;

/**
 * Represents a clean/dirty cache line.
 */
public class SimpleLine extends AbstractCacheLine {
    boolean dirty;

    SimpleLine(long blockAddress, byte[] data, long timestamp) {
        super(blockAddress, data, timestamp);
        this.dirty = false;
    }
}