package net.eabbott.riscvm.context.cache;

/*
* A data structure class for all cache configurations.
* */
public class CacheContext {
    public CacheAssociativity ca;
    public long size;
    public int blockSize;
    public boolean isWriteBack;
    public CacheEvictionPolicy eviction;

    public CacheContext(CacheAssociativity ca, long size, int blockSize, boolean isWriteBack, CacheEvictionPolicy eviction) {
        this.ca = ca;
        this.size = size;
        this.blockSize = blockSize;
        this.isWriteBack = isWriteBack;
        this.eviction = eviction;
    }
}
