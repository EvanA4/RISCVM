package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;

public abstract class AbstractCacheLine {
    final long blockAddress;
    final byte[] data;
    long frequency;
    final long loadedAt;
    long lastUsed;

    AbstractCacheLine(long blockAddress, byte[] data, long timestamp) {
        this.blockAddress = blockAddress;
        this.data = data;
        this.frequency = 1;
        this.loadedAt = timestamp;
        this.lastUsed = timestamp;
    }

    public void touch(long clock) {
        this.frequency++;
        this.lastUsed = ++clock;
    }

    /**
     * Defines whether one line is "younger" than another for line eviction.
     */
    public boolean isYoungerThan(AbstractCacheLine candidate, CacheEvictionPolicy evictionPolicy) {
        return switch (evictionPolicy) {
            case FIFO -> candidate.loadedAt < this.loadedAt;
            case LRU  -> candidate.lastUsed < this.lastUsed;
            case LFU  -> candidate.frequency < this.frequency;
        };
    }
}
