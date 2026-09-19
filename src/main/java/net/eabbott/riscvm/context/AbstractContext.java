package net.eabbott.riscvm.context;

import net.eabbott.riscvm.util.Nullable;
import net.eabbott.riscvm.util.VMLogger;

/*
* A class representing the initial values for each virtual machine configuration.
* Also requires any Context class to provide a dump function for debugging.
* */
public abstract class AbstractContext {
    public @Nullable String configFile = null;
    public @Nullable String elfFile = null;
    public @Nullable String outputFile = null;
    public boolean allowRV32M = true;
    public boolean allowRV32A = true;
    public long ramSize = 1_048_576;
    public int numHarts = 4;
    public int cycleFrequency = 100;
    public int cacheDepth = 3;
    public CacheContext l1Cache = new CacheContext(
        new CacheAssociativity(CacheAssociativityType.DIRECT_MAPPED, -1),
        1024, 4, false,
        EvictionPolicy.FIFO
    );
    public CacheContext l2Cache = new CacheContext(
            new CacheAssociativity(CacheAssociativityType.SET_ASSOC, 3),
            2048, 5, false,
            EvictionPolicy.LRU
    );
    public CacheContext l3Cache = new CacheContext(
            new CacheAssociativity(CacheAssociativityType.DIRECT_MAPPED, -1),
            4096, 6, true,
            EvictionPolicy.LFU
    );
    public CacheCoherency cacheCoherency = CacheCoherency.SNOOP;
    public int branchPredictionRows = 64;
    public int defaultPrediction = 0;
    public boolean allowMMU = true;
    public int numTLBSlots = 8;
    public EvictionPolicy tlbEviction = EvictionPolicy.LRU;
    public VMLogger logger = new VMLogger();

    public abstract void dump();
}
