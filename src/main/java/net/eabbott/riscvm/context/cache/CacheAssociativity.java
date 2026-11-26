package net.eabbott.riscvm.context.cache;

/**
* A simple data structure for storing a cache's associativity.
* */
public class CacheAssociativity {
    public CacheAssociativityType type;
    public int ways;

    // The all-args constructor for the CacheAssociativity class
    public CacheAssociativity(CacheAssociativityType type, int ways) {
        this.type = type;
        this.ways = ways;
    }
}

