package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheCoherency;
import net.eabbott.riscvm.machine.memory.BackingStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
* The L1 cache for a hart. This is the hardest class I have ever written in my programming career.
* */
public final class L1Cache implements BackingStore {
    // The snoopy bus lock and list of all L1 caches
    private static final Object BUS_LOCK = new Object();
    private static final Map<Integer, L1Cache> CACHES = new ConcurrentHashMap<>();

    // The DIR coherency properties
    private static final Map<Long, Map<Integer, MESILine>> DIRECTORY =
        new ConcurrentHashMap<>();
    private static final Map<Long, Object> DIRECTORY_LOCKS =
        new ConcurrentHashMap<>();

    // The local cache's lock
    private final Object localLock = new Object();

    private final int hartId;
    private final int blockSize;
    private final boolean writeBack;
    private final BackingStore backingStore;
    private final CacheCoherency coherency;

    private final MESICacheData lines;
    private long clock;

    public L1Cache(
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
        this.writeBack = context.isWriteBack;
        this.coherency = Objects.requireNonNull(coherency, "coherency");
        this.lines = new MESICacheData(context);

        synchronized (BUS_LOCK) {
            L1Cache previous = CACHES.putIfAbsent(hartId, this);
            if (previous != null) {
                throw new IllegalStateException(
                        "Cache already registered for hart " + hartId);
            }
        }
    }

    @Override
    public MemoryResult read(long address, int size) {
        byte[] output = new byte[size];

        // Awkward loop tracks reads spanning multiple blocks
        int copied = 0;
        while (copied < size) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(size - copied, blockSize - offset);

            MemoryResult result = readBlockRange(block, offset, count);
            if (result.shouldWait()) return new MemoryResult(true, null);

            System.arraycopy(result.data(), 0, output, copied, count);
            copied += count;
        }

        return new MemoryResult(false, output);
    }

    private MemoryResult readBlockRange(long block, int offset, int count) {
        // Best-case scenario: local read is possible
        synchronized (localLock) {
            MESILine line = lines.read(block);
            if (isValid(line)) {
                line.touch(++clock);
                byte[] range = Arrays.copyOfRange(line.data, offset, offset + count);
                return new MemoryResult(false, range);
            }
        }

        // Painful scenario: local read is not possible, MESI operation needed
        synchronized (this.getCoherencyLock(block)) {
            // Check again if another hart changed local cache while acquiring coherency lock
            synchronized (localLock) {
                MESILine line = lines.read(block);
                if (isValid(line)) {
                    line.touch(++clock);
                    byte[] range = Arrays.copyOfRange(line.data, offset, offset + count);
                    return new MemoryResult(false, range);
                }
            }

            // MESI operation is still needed :(
            byte[] data = findPeerData(block);
            boolean hadValidPeer = data != null;

            synchronized (localLock) {
                // If block was not found among peers, access backing store
                if (data == null) {
                    MemoryResult result = readBackingBlock(block);
                    if (result.shouldWait()) return new MemoryResult(true, null);
                    data = result.data();
                }

                // Add/update line in cache and request stall
                installLine(block, data, hadValidPeer ? MESI.SHARED : MESI.EXCLUSIVE);
                return new MemoryResult(true, null);
            }
        }
    }

    @Override
    public MemoryResult write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");

        // Awkward loop tracks writes spanning multiple blocks
        int copied = 0;
        while (copied < data.length) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(data.length - copied, blockSize - offset);

            MemoryResult result = writeBlockRange(block, offset, data, copied, count);
            if (result.shouldWait()) return new MemoryResult(true, null);

            copied += count;
        }

        return new MemoryResult(false, null);
    }

    private MemoryResult writeBlockRange(
            long block, int offset, byte[] source, int sourceOffset, int count) {

        // Best-case scenario: local write is possible
        synchronized (localLock) {
            MESILine line = lines.read(block);
            if (isValid(line)
                    && (line.state == MESI.MODIFIED
                    || line.state == MESI.EXCLUSIVE)) {
                System.arraycopy(source, sourceOffset, line.data, offset, count);
                line.touch(++clock);

                if (line.state == MESI.EXCLUSIVE) {
                    line.state = MESI.MODIFIED;
                }

                if (!writeBack) {
                    backingStore.write(block, line.data);
                    line.state = MESI.EXCLUSIVE;
                }
                
                return new MemoryResult(false, null);
            }
        }

        // Painful scenario: local write is not possible, MESI operation needed
        synchronized (this.getCoherencyLock(block)) {
            // Check again if another hart changed local cache while acquiring coherency lock
            synchronized (localLock) {
                MESILine line = lines.read(block);
                if (
                    isValid(line) &&
                    (line.state == MESI.MODIFIED || line.state == MESI.EXCLUSIVE))
                {
                    System.arraycopy(source, sourceOffset, line.data, offset, count);
                    line.touch(++clock);

                    if (line.state == MESI.EXCLUSIVE) {
                        line.state = MESI.MODIFIED;
                    }

                    if (!writeBack) {
                        backingStore.write(block, line.data);
                        line.state = MESI.EXCLUSIVE;
                    }

                    return new MemoryResult(false, null);
                }
            }

            // MESI operation is still needed :(
            byte[] data;
            synchronized (localLock) {
                MESILine line = lines.read(block);
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
                MemoryResult result = readBackingBlock(block);
                if (result.shouldWait()) return new MemoryResult(true, null);
                data = result.data();
            }

            if (coherency != CacheCoherency.NONE) {
                invalidatePeerCopies(block);
            }

            // Finally, read to local cache and request stall
            synchronized (localLock) {
                MESILine line = installLine(block, data, MESI.EXCLUSIVE);
                registerDirectoryLine(block, line);
                return new MemoryResult(true, null);
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

        for (L1Cache peer : peerCaches(block)) {
            synchronized (peer.localLock) {
                MESILine peerLine = peer.lines.read(block);
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
        for (L1Cache peer : peerCaches(block)) {
            synchronized (peer.localLock) {
                MESILine peerLine = peer.lines.read(block);
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
    private Iterable<L1Cache> peerCaches(long block) {
        Map<Integer, L1Cache> result = new HashMap<>();

        if (coherency == CacheCoherency.DIR) {
            Map<Integer, MESILine> sharers = DIRECTORY.get(block);
            if (sharers != null) {
                for (Integer id : sharers.keySet()) {
                    if (id != hartId) {
                        L1Cache peer = CACHES.get(id);
                        if (peer != null) {
                            result.put(id, peer);
                        }
                    }
                }
            }
        } else if (coherency == CacheCoherency.SNOOP) {
            for (Map.Entry<Integer, L1Cache> entry : CACHES.entrySet()) {
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
    private MemoryResult readBackingBlock(long block) {
        MemoryResult result = backingStore.read(block, blockSize);
        if (result == null || result.data().length != blockSize) {
            throw new IllegalStateException(
                "Backing store returned an invalid cache block.");
        }
        return result;
    }

    /**
     * Adds a line to the local cache. Should only be called with local lock held.
     */
    private MESILine installLine(long block, byte[] data, MESI state) {
        MESILine evicted = this.lines.evictIfFull(block);
        if (evicted.state == MESI.MODIFIED) unregisterDirectoryLine(evicted.blockAddress);
        MESILine line = new MESILine(block, Arrays.copyOf(data, blockSize), state, ++clock);
        lines.write(line);
        line.touch(++clock);
        registerDirectoryLine(block, line);
        return line;
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
     * Returns whether a line exists and is not invalid.
     */
    private static boolean isValid(MESILine line) {
        return line != null && line.state != MESI.INVALID;
    }

    /**
     * Converts an address into a block-aligned byte address.
     */
    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }

    /**
     * Adds the local cache's corresponding hart to the directory for a given block.
     */
    private void registerDirectoryLine(long block, MESILine line) {
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

        Map<Integer, MESILine> sharers = DIRECTORY.get(block);
        if (sharers != null) {
            sharers.remove(hartId);
            if (sharers.isEmpty()) {
                DIRECTORY.remove(block);
            }
        }
    }
}
