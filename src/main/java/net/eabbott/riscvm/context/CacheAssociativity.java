package net.eabbott.riscvm.context;

public class CacheAssociativity {
    public CacheAssociativityType type;
    public int ways;

    public CacheAssociativity(CacheAssociativityType type, int ways) {
        this.type = type;
        this.ways = ways;
    }
}

