package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheAssociativityType;
import net.eabbott.riscvm.context.cache.CacheContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MESICacheData {
    private final CacheContext context;
    private final int numLines;
    private Map<Long, MESILine> faLines = null;
    private List<Map<Long, MESILine>> saLines = null;
    private List<MESILine> daLines = null;

    public MESICacheData(CacheContext context) {
        this.context = context;
        this.numLines = Math.toIntExact(context.size / context.blockSize);
        switch (context.ca.type) {
            case DIRECT_MAPPED -> {
                this.daLines = new ArrayList<>(this.numLines);
                for (int i = 0; i < this.numLines; ++i) this.daLines.add(null);
            }
            case SET_ASSOC -> {
                this.saLines = new ArrayList<>();
                for (int i = 0; i < this.numLines / context.ca.ways; ++i) {
                    this.saLines.add(new ConcurrentHashMap<>());
                }
            }
            case FULL_ASSOC -> {
                this.faLines = new ConcurrentHashMap<>();
            }
        }
    }

    public void invalidateAll() {
        switch (context.ca.type) {
            case DIRECT_MAPPED -> {
                for (int i = 0; i < this.numLines; ++i) this.daLines.set(i, null);
            }
            case SET_ASSOC -> {
                for (int i = 0; i < this.numLines / context.ca.ways; ++i) {
                    this.saLines.get(i).clear();
                }
            }
            case FULL_ASSOC -> {
                this.faLines.clear();
            }
        }
    }

    /**
     * Returns whether a line is present in the local cache.
     */
    private boolean hasLine(long block) {
        return switch (this.context.ca.type) {
            case DIRECT_MAPPED -> {
                int index = Math.floorMod(
                        Math.floorDiv(block, this.context.blockSize), this.daLines.size());
                if (daLines.get(index) == null) yield false;
                else yield daLines.get(index).blockAddress == block;
            }
            case SET_ASSOC -> {
                int index = Math.floorMod(
                        Math.floorDiv(block, this.context.blockSize), this.saLines.size());
                Map<Long, MESILine> map = saLines.get(index);
                yield map.containsKey(block);
            }
            case FULL_ASSOC -> this.faLines.containsKey(block);
        };
    }

    /**
     * Reads a line from the local cache. Should only be called with local lock held.
     */
    public MESILine read(long block) {
        if (!this.hasLine(block)) return null;
        return switch (this.context.ca.type) {
            case DIRECT_MAPPED -> {
                int index = Math.floorMod(
                    Math.floorDiv(block, this.context.blockSize), this.daLines.size());
                yield daLines.get(index);
            }
            case SET_ASSOC -> {
                int index = Math.floorMod(
                    Math.floorDiv(block, this.context.blockSize), this.saLines.size());
                Map<Long, MESILine> map = saLines.get(index);
                yield map.get(block);
            }
            case FULL_ASSOC -> this.faLines.get(block);
        };
    }

    /**
     * Adds a line to the local cache. Should only be called with local lock held and after confirming there is space.
     */
    public void write(MESILine line) {
        switch (this.context.ca.type) {
            case DIRECT_MAPPED -> {
                int index = Math.floorMod(
                    Math.floorDiv(line.blockAddress, this.context.blockSize), this.daLines.size());
                daLines.set(index, line);
            }
            case SET_ASSOC -> {
                int index = Math.floorMod(
                    Math.floorDiv(line.blockAddress, this.context.blockSize), this.saLines.size());
                Map<Long, MESILine> map = saLines.get(index);
                map.put(line.blockAddress, line);
            }
            case FULL_ASSOC -> {
                faLines.put(line.blockAddress, line);
            }
        }
    }

    /**
     * Finds a line for removal from a supposedly full, local cache.
     * This should only be called with both coherency and local locks held.
     * If the cache is not full, null is returned.
     * */
    public MESILine findVictim(long block) {
        if (this.context.ca.type == CacheAssociativityType.DIRECT_MAPPED) {
            int index = Math.floorMod(
                Math.floorDiv(block, this.context.blockSize), this.daLines.size());
            return daLines.get(index);
        }

        Map<Long, MESILine> map = null;
        int maxSize = Integer.MAX_VALUE;
        switch (this.context.ca.type) {
            case SET_ASSOC -> {
                int index = Math.floorMod(
                        Math.floorDiv(block, this.context.blockSize), this.saLines.size());
                map = saLines.get(index);
                maxSize = this.context.ca.ways;
            }
            case FULL_ASSOC -> {
                map = faLines;
                maxSize = numLines;
            }
        };
        if (map == null || map.size() < maxSize) return null;

        // Iterate through every line, preferring invalid lines over older lines
        MESILine victim = null;
        for (MESILine candidate : map.values()) {
            if (candidate.state == MESI.INVALID) {
                victim = candidate;
                break;
            }
            if (victim == null || candidate.isYoungerThan(candidate, this.context.eviction)) {
                victim = candidate;
            }
        }

        return victim;
    }

    /**
     * Removes a line and returns its block address from the full, local cache.
     * This should only be called with both coherency and local locks held and
     * the backing store successfully wrote back the line (if it was dirty).
     * If the cache is not full, null is returned.
     * */
    public void evictBlock(long block) {
        if (this.context.ca.type == CacheAssociativityType.DIRECT_MAPPED) {
            int index = Math.floorMod(
                Math.floorDiv(block, this.context.blockSize), this.daLines.size());
            daLines.set(index, null);
        }

        switch (this.context.ca.type) {
            case SET_ASSOC -> {
                int index = Math.floorMod(
                    Math.floorDiv(block, this.context.blockSize), this.saLines.size());
                saLines.get(index).remove(block);
            }
            case FULL_ASSOC -> faLines.remove(block);
        }
    }
}
