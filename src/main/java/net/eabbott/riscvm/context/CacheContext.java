package net.eabbott.riscvm.context;

public class CacheContext {
    public CacheAssociativity ca;
    public long size;
    public int blockSize;
    public boolean isWriteBack;
    public EvictionPolicy eviction;

    public CacheContext(CacheAssociativity ca, long size, int blockSize, boolean isWriteBack, EvictionPolicy eviction) {
        this.ca = ca;
        this.size = size;
        this.blockSize = blockSize;
        this.isWriteBack = isWriteBack;
        this.eviction = eviction;
    }
}
