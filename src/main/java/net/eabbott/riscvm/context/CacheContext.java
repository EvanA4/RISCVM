package net.eabbott.riscvm.context;

public class CacheContext {
    public CacheAssociativity ca;
    public int ways;
    public long size;
    public int block_size;
    public boolean is_write_back;
    public EvictionPolicy eviction;

    public CacheContext(CacheAssociativity ca, int ways, long size, int block_size, boolean is_write_back, EvictionPolicy eviction) {
        this.ca = ca;
        this.ways = ways;
        this.size = size;
        this.block_size = block_size;
        this.is_write_back = is_write_back;
        this.eviction = eviction;
    }
}
