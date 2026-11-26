package net.eabbott.riscvm.machine.memory.cache;

/**
 * Represents a cache line with a MESI state.
 */
public class MESILine extends AbstractCacheLine {
    MESI state;

    MESILine(long blockAddress, byte[] data, MESI state, long timestamp) {
        super(blockAddress, data, timestamp);
        this.state = state;
    }
}