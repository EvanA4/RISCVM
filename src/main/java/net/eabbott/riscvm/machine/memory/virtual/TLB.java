package net.eabbott.riscvm.machine.memory.virtual;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;

import java.util.Arrays;
import java.util.Objects;

public final class TLB {
    private final TLBEntry[] entries;
    private final long[] insertedAt;
    private final CacheEvictionPolicy policy;

    private long clock;

    public TLB(int capacity, CacheEvictionPolicy policy) {
        this.entries = new TLBEntry[capacity];
        this.insertedAt = new long[capacity];
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized int capacity() {
        return entries.length;
    }

    public synchronized boolean isEnabled() {
        return entries.length > 0;
    }

    /**
     * Looks up a virtual address. Returns null if disabled or if there is no hit.
     */
    public synchronized TLBEntry lookup(long virtualAddress) {
        if (!isEnabled()) return null;

        for (TLBEntry entry : entries) {
            if (entry != null && entry.matches(virtualAddress)) {
                entry.tick(++clock);
                return entry;
            }
        }

        return null;
    }

    /**
     * Inserts an entry, replacing an existing entry for the same virtual page
     * or evicting an entry according to the configured policy.
     */
    public synchronized void insert(TLBEntry entry) {
        if (!isEnabled()) return;

        // Replace an existing translation for the same page.
        for (int i = 0; i < entries.length; i++) {
            TLBEntry current = entries[i];

            if (current != null
                    && current.getLevel() == entry.getLevel()
                    && current.getVpn() == entry.getVpn()) {
                entries[i] = entry;
                insertedAt[i] = ++clock;
                return;
            }
        }

        // Prefer an empty slot.
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] == null) {
                entries[i] = entry;
                insertedAt[i] = ++clock;
                return;
            }
        }

        // TLB is full: evict one entry.
        int victim = chooseVictim();
        entries[victim] = entry;
        insertedAt[victim] = ++clock;
    }

    /**
     * Invalidates all entries. Use for sfence.vma with rs1=x0 and rs2=x0.
     */
    public synchronized void invalidateAll() {
        Arrays.fill(entries, null);
        Arrays.fill(insertedAt, 0);
    }

    /**
     * Invalidates entries matching a virtual address. This handles page
     * invalidation for either 4 KiB or 4 MiB entries.
     */
    public synchronized void invalidateAddress(long virtualAddress) {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != null && entries[i].matches(virtualAddress)) {
                entries[i] = null;
                insertedAt[i] = 0;
            }
        }
    }

    private int chooseVictim() {
        int victim = 0;

        for (int i = 1; i < entries.length; i++) {
            if (isOlder(entries[i], i, entries[victim], victim)) {
                victim = i;
            }
        }

        return victim;
    }

    private boolean isOlder(TLBEntry candidate, int candidateIndex,
                            TLBEntry current, int currentIndex) {
        return switch (policy) {
            case FIFO -> insertedAt[candidateIndex] < insertedAt[currentIndex];
            case LRU -> candidate.getLastUsed() < current.getLastUsed();
            case LFU -> candidate.getHitCount() < current.getHitCount();
        };
    }
}
