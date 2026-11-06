package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheCoherency;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;
import net.eabbott.riscvm.machine.memory.BackingStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The L2 cache for a hart.
 * */
public final class L2Cache implements BackingStore {
    // The snoopy bus lock and list of all L1 caches
    private static final Object BUS_LOCK = new Object();
    private static final Map<Integer, L2Cache> CACHES = new ConcurrentHashMap<>();

    // The DIR coherency properties
    private static final Map<Long, Map<Integer, L12Line>> DIRECTORY =
            new ConcurrentHashMap<>();
    private static final Map<Long, Object> DIRECTORY_LOCKS =
            new ConcurrentHashMap<>();

    // The local cache's lock
    private final Object localLock = new Object();

    private final int hartId;
    private final int blockSize;
    private final int capacityLines;
    private final boolean writeBack;
    private final BackingStore backingStore;
    private final CacheEvictionPolicy evictionPolicy;
    private final CacheCoherency coherency;

    private final Map<Long, L12Line> lines = new HashMap<>();
    private long clock;

    public L2Cache(
            CacheCoherency coherency,
            CacheContext context,
            int hartId,
            BackingStore backingStore) {

        Objects.requireNonNull(context, "context");
        this.backingStore = Objects.requireNonNull(backingStore, "backingStore");

        if (context.blockSize <= 0 || context.size < context.blockSize) {
            throw new IllegalArgumentException(
                    "Cache size and block size must be positive.");
        }

        this.hartId = hartId;
        this.blockSize = context.blockSize;
        this.capacityLines = Math.toIntExact(context.size / context.blockSize);
        this.writeBack = context.isWriteBack;
        this.evictionPolicy = Objects.requireNonNull(context.eviction, "eviction");
        this.coherency = Objects.requireNonNull(coherency, "coherency");

        synchronized (BUS_LOCK) {
            L2Cache previous = CACHES.putIfAbsent(hartId, this);
            if (previous != null) {
                throw new IllegalStateException(
                        "Cache already registered for hart " + hartId);
            }
        }
    }

    @Override
    public byte[] read(long address, int size) {
        byte[] result = new byte[size];

        // Awkward loop tracks reads spanning multiple blocks
        int copied = 0;
        while (copied < size) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(size - copied, blockSize - offset);

            byte[] chunk = readBlockRange(block, offset, count);
            System.arraycopy(chunk, 0, result, copied, count);
            copied += count;
        }

        return result;
    }

    private byte[] readBlockRange(long block, int offset, int count) {
        // Best-case scenario: local read is possible
        synchronized (localLock) {
            L12Line line = lines.get(block);
            if (isValid(line)) {
                touch(line);
                return Arrays.copyOfRange(line.data, offset, offset + count);
            }
        }

        // Painful scenario: local read is not possible, MESI operation needed
        synchronized (this.getCoherencyLock(block)) {
            // Check again if another hart changed local cache while acquiring coherency lock
            synchronized (localLock) {
                L12Line line = lines.get(block);
                if (isValid(line)) {
                    touch(line);
                    return Arrays.copyOfRange(line.data, offset, offset + count);
                }
            }

            // MESI operation is still needed :(
            byte[] data = findPeerData(block);
            boolean hadValidPeer = data != null;

            synchronized (localLock) {
                // This shouldn't be possible, but check anyway because I'm scared
                L12Line line = lines.get(block);
                if (isValid(line)) {
                    touch(line);
                    return Arrays.copyOfRange(line.data, offset, offset + count);
                }

                // If block was not found among peers, access backing store
                if (data == null) {
                    data = readBackingBlock(block);
                }

                // Add/update line in cache and return read
                line = installLine(block, data,
                        hadValidPeer ? MESI.SHARED : MESI.EXCLUSIVE);
                touch(line);
                return Arrays.copyOfRange(line.data, offset, offset + count);
            }
        }
    }

    @Override
    public void write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");

        // Awkward loop tracks writes spanning multiple blocks
        int copied = 0;
        while (copied < data.length) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(data.length - copied, blockSize - offset);

            writeBlockRange(block, offset, data, copied, count);
            copied += count;
        }
    }

    private void writeBlockRange(
            long block, int offset, byte[] source, int sourceOffset, int count) {

        // Best-case scenario: local write is possible
        synchronized (localLock) {
            L12Line line = lines.get(block);
            if (isValid(line)
                    && (line.state == MESI.MODIFIED
                    || line.state == MESI.EXCLUSIVE)) {
                System.arraycopy(source, sourceOffset, line.data, offset, count);
                touch(line);

                if (line.state == MESI.EXCLUSIVE) {
                    line.state = MESI.MODIFIED;
                }

                if (!writeBack) {
                    backingStore.write(block, line.data);
                    line.state = MESI.EXCLUSIVE;
                }
                return;
            }
        }

        // Painful scenario: local write is not possible, MESI operation needed
        synchronized (this.getCoherencyLock(block)) {
            // Check again if another hart changed local cache while acquiring coherency lock
            synchronized (localLock) {
                L12Line line = lines.get(block);
                if (
                        isValid(line) &&
                                (line.state == MESI.MODIFIED || line.state == MESI.EXCLUSIVE))
                {
                    System.arraycopy(source, sourceOffset, line.data, offset, count);
                    touch(line);

                    if (line.state == MESI.EXCLUSIVE) {
                        line.state = MESI.MODIFIED;
                    }

                    if (!writeBack) {
                        backingStore.write(block, line.data);
                        line.state = MESI.EXCLUSIVE;
                    }
                    return;
                }
            }

            // MESI operation is still needed :(
            byte[] data;
            synchronized (localLock) {
                L12Line line = lines.get(block);
                data = isValid(line)
                        ? Arrays.copyOf(line.data, blockSize)
                        : null;
            }

            // If line not in cache, look for line across peers
            if (data == null) {
                data = findPeerData(block);
            }

            // If no peer has the line, access backing store
            if (data == null) {
                data = readBackingBlock(block);
            }

            if (coherency != CacheCoherency.NONE) {
                invalidatePeerCopies(block);
            }

            // Finally, write to local cache
            synchronized (localLock) {
                // ...but first make sure the line exists
                L12Line line = lines.get(block);
                if (!isValid(line)) {
                    line = installLine(block, data, MESI.EXCLUSIVE);
                }

                // ...then write to local cache
                System.arraycopy(source, sourceOffset, line.data, offset, count);
                touch(line);

                // ...and update backing store if write-through
                if (writeBack) {
                    line.state = MESI.MODIFIED;
                } else {
                    backingStore.write(block, line.data);
                    line.state = MESI.EXCLUSIVE;
                }

                registerDirectoryLine(block, line);
            }
        }
    }

    /**
     * Tries to find block amongst peers. Should only be called with coherency lock held.
     */
    private byte[] findPeerData(long block) {
        if (coherency == CacheCoherency.NONE) {
            return null;
        }

        for (L2Cache peer : peerCaches(block)) {
            synchronized (peer.localLock) {
                L12Line peerLine = peer.lines.get(block);
                if (!isValid(peerLine)) {
                    continue;
                }

                byte[] data = Arrays.copyOf(peerLine.data, blockSize);
                if (peerLine.state == MESI.MODIFIED) {
                    peer.backingStore.write(block, peerLine.data);
                    peerLine.state = MESI.SHARED;
                } else if (peerLine.state == MESI.EXCLUSIVE) {
                    peerLine.state = MESI.SHARED;
                }
                return data;
            }
        }

        return null;
    }

    /**
     * Invalidates block in other peers' caches. Should only be called with coherency lock held.
     * */
    private void invalidatePeerCopies(long block) {
        for (L2Cache peer : peerCaches(block)) {
            synchronized (peer.localLock) {
                L12Line peerLine = peer.lines.get(block);
                if (isValid(peerLine)) {
                    peerLine.state = MESI.INVALID;
                    peer.unregisterDirectoryLine(block);
                }
            }
        }
    }

    /**
     * Fetches all peers' caches for MESI operations. Should only be called with coherency lock held.
     */
    private Iterable<L2Cache> peerCaches(long block) {
        Map<Integer, L2Cache> result = new HashMap<>();

        if (coherency == CacheCoherency.DIR) {
            Map<Integer, L12Line> sharers = DIRECTORY.get(block);
            if (sharers != null) {
                for (Integer id : sharers.keySet()) {
                    if (id != hartId) {
                        L2Cache peer = CACHES.get(id);
                        if (peer != null) {
                            result.put(id, peer);
                        }
                    }
                }
            }
        } else if (coherency == CacheCoherency.SNOOP) {
            for (Map.Entry<Integer, L2Cache> entry : CACHES.entrySet()) {
                if (entry.getKey() != hartId) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result.values();
    }

    /**
     * Tries to read block from backing store. Should only be called with coherency lock held.
     */
    private byte[] readBackingBlock(long block) {
        byte[] data = backingStore.read(block, blockSize);
        if (data == null || data.length != blockSize) {
            throw new IllegalStateException(
                    "Backing store returned an invalid cache block.");
        }
        return data;
    }

    /**
     * Adds a line to the local cache. Should only be called with local lock held.
     */
    private L12Line installLine(long block, byte[] data, MESI state) {
        evictIfFull();
        L12Line line = new L12Line(block, Arrays.copyOf(data, blockSize),
                state, ++clock);
        lines.put(block, line);
        registerDirectoryLine(block, line);
        return line;
    }

    /**
     * Removes a line from the full, local cache. Should only be called with both coherency and local locks held.
     * */
    private void evictIfFull() {
        if (lines.size() < capacityLines) {
            return;
        }

        // Iterate through every line, preferring invalid lines over "earlier" lines
        L12Line victim = null;
        for (L12Line candidate : lines.values()) {
            if (candidate.state == MESI.INVALID) {
                victim = candidate;
                break;
            }
            if (victim == null || isEarlierVictim(candidate, victim)) {
                victim = candidate;
            }
        }

        if (victim == null) {
            throw new IllegalStateException("Cache has no evictable line.");
        }

        // Should only be true for write-back caching
        if (victim.state == MESI.MODIFIED) {
            backingStore.write(victim.blockAddress, victim.data);
        }

        unregisterDirectoryLine(victim.blockAddress);
        lines.remove(victim.blockAddress);
    }

    /**
     * Defines whether one line is "earlier" than another for line eviction.
     */
    private boolean isEarlierVictim(L12Line candidate, L12Line current) {
        return switch (evictionPolicy) {
            case FIFO -> candidate.loadedAt < current.loadedAt;
            case LRU -> candidate.lastUsed < current.lastUsed;
            case LFU -> candidate.frequency < current.frequency;
        };
    }

    /**
     * Fetches appropriate lock for DIR and SNOOP coherency.
     */
    private Object getCoherencyLock(long block) {
        return coherency == CacheCoherency.DIR
                ? DIRECTORY_LOCKS.computeIfAbsent(block, ignored -> new Object())
                : BUS_LOCK;
    }

    /**
     * Updates a line in cache.
     */
    private void touch(L12Line line) {
        line.frequency++;
        line.lastUsed = ++clock;
    }

    /**
     * Returns whether a line exists and is not invalid.
     */
    private static boolean isValid(L12Line line) {
        return line != null && line.state != MESI.INVALID;
    }

    /**
     * Converts an address into a block address.
     */
    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }

    /**
     * Adds the local cache's corresponding hart to the directory for a given block.
     */
    private void registerDirectoryLine(long block, L12Line line) {
        if (coherency != CacheCoherency.DIR || line == null) {
            return;
        }
        DIRECTORY.computeIfAbsent(block, ignored -> new ConcurrentHashMap<>())
                .put(hartId, line);
    }

    /**
     * Removes the local cache's corresponding hart from the directory for a given block. 
     */
    private void unregisterDirectoryLine(long block) {
        if (coherency != CacheCoherency.DIR) {
            return;
        }

        Map<Integer, L12Line> sharers = DIRECTORY.get(block);
        if (sharers != null) {
            sharers.remove(hartId);
            if (sharers.isEmpty()) {
                DIRECTORY.remove(block);
            }
        }
    }
}
