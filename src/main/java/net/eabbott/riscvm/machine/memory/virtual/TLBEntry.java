package net.eabbott.riscvm.machine.memory.virtual;

public final class TLBEntry {
    private final int level;
    private final int vpn;
    private final long ppn;

    private final boolean readable;
    private final boolean writable;
    private final boolean executable;
    private final boolean user;
    private final boolean global;
    private final boolean accessed;
    private final boolean dirty;

    // For LRU and LFU replacement
    private long lastUsed;
    private long hitCount;

    public TLBEntry(
        int level,
        int vpn,
        long ppn,
        boolean readable,
        boolean writable,
        boolean executable,
        boolean user,
        boolean global,
        boolean accessed,
        boolean dirty,
        long lastUsed
    ) {
        this.level = level;
        this.vpn = vpn & (level == 0 ? 0xFFFFF : 0x3FF);
        this.ppn = ppn & (level == 0 ? 0x3FFFFFL : 0xFFFL);

        this.readable = readable;
        this.writable = writable;
        this.executable = executable;
        this.user = user;
        this.global = global;
        this.accessed = accessed;
        this.dirty = dirty;

        this.lastUsed = lastUsed;
        this.hitCount = 0;
    }

    /**
     * Whether a virtual address matches the current TLB entry.
     */
    public boolean matches(long virtualAddress) {
        int vpn1 = (int) ((virtualAddress >>> 22) & 0x3FF);
        int vpn0 = (int) ((virtualAddress >>> 12) & 0x3FF);

        if (level == 1) {
            return vpn == vpn1;
        }

        int fullVpn = (vpn1 << 10) | vpn0;
        return vpn == fullVpn;
    }

    /**
     * Converts a virtual address into its physical address according to the current TLB entry.
     */
    public long translate(long virtualAddress) {
        long pageOffset = virtualAddress & 0xFFFL;

        if (level == 1) {
            // Level-1 leaf translation
            long vpn0 = (virtualAddress >>> 12) & 0x3FFL;
            long physicalPpn = (ppn << 10) | vpn0;
            return (physicalPpn << 12) | pageOffset;
        }

        // Level-0 leaf translation
        return (ppn << 12) | pageOffset;
    }

    public boolean permitsRead()      { return readable; }
    public boolean permitsWrite()     { return writable; }
    public boolean permitsExecute()   { return executable; }
    public boolean isUserPage()       { return user; }
    public boolean isGlobal()         { return global; }
    public boolean isAccessed()       { return accessed; }
    public boolean isDirty()          { return dirty; }

    public int getLevel()             { return level; }
    public int getVpn()               { return vpn; }
    public long getPpn()              { return ppn; }
    public long getLastUsed()         { return lastUsed; }
    public long getHitCount()         { return hitCount; }

    /*
    * Updates replacement values for the given entry.
    * */
    public void tick(long timestamp) {
        lastUsed = timestamp;
        hitCount++;
    }
}
