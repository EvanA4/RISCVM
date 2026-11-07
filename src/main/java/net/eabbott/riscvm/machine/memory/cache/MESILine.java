package net.eabbott.riscvm.machine.memory.cache;

public class MESILine extends AbstractCacheLine {
    MESI state;

    MESILine(long blockAddress, byte[] data, MESI state, long timestamp) {
        super(blockAddress, data, timestamp);
        this.state = state;
    }
}