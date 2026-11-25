package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheCoherency;
import net.eabbott.riscvm.machine.memory.BackingStore;
import net.eabbott.riscvm.util.VMLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The L1 cache for a hart.
 * */
public final class L1Cache implements BackingStore {
    private record InstallationResult(int shouldWait, MESILine data) {}

    private final VMLogger logger = VMLogger.getInstance();

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
        this.blockSize = 1 << context.blockSize;
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

    public void invalidateAll() {
        // Shallow invalidation ignores dirty entries
        lines.invalidateAll();
    }

    @Override
    public MemoryResult read(long address, int size) {
        int toWait = 0;
        byte[] output = new byte[size];

        // Awkward loop tracks reads spanning multiple blocks
        int copied = 0;
        while (copied < size) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(size - copied, blockSize - offset);

            MemoryResult result = readBlockRange(block, offset, count);
            toWait += result.shouldWait();

            System.arraycopy(result.data(), 0, output, copied, count);
            copied += count;
        }

        return new MemoryResult(toWait, output);
    }

    private MemoryResult readBlockRange(long block, int offset, int count) {
        int toWait = 0;

        // Best-case scenario: local read is possible
        synchronized (localLock) {
            MESILine line = lines.read(block);
            if (isValid(line)) {
                line.touch(++clock);
                byte[] range = Arrays.copyOfRange(line.data, offset, offset + count);
                return new MemoryResult(0, range);
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
                    return new MemoryResult(0, range);
                }
            }

            // MESI operation is still needed :(
            MemoryResult result = findPeerData(block);
            toWait += result.shouldWait();
            byte[] data = result.data();
            boolean hadValidPeer = data != null;

            synchronized (localLock) {
                // If block was not found among peers, access backing store
                if (data == null) {
                    result = backingStore.read(block, blockSize);
                    toWait += 1 + result.shouldWait();
                    data = result.data();
                }

                // Add/update line in cache and request stall
                InstallationResult instResult = installLine(block, data, hadValidPeer ? MESI.SHARED : MESI.EXCLUSIVE);
                toWait += instResult.shouldWait();
                byte[] range = Arrays.copyOfRange(data, offset, offset + count);
                return new MemoryResult(toWait, range);
            }
        }
    }

    @Override
    public MemoryResult write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");
        int toWait = 0;

        // Awkward loop tracks writes spanning multiple blocks
        int copied = 0;
        while (copied < data.length) {
            long currentAddress = address + copied;
            long block = blockAddress(currentAddress);
            int offset = (int) (currentAddress - block);
            int count = Math.min(data.length - copied, blockSize - offset);

            MemoryResult result = writeBlockRange(block, offset, data, copied, count);
            toWait += result.shouldWait();

            copied += count;
        }

        logger.verbose("[%d] L1 CACHE: returning from entire write.", hartId);
        return new MemoryResult(toWait, null);
    }

    private MemoryResult writeBlockRange(
            long block, int offset, byte[] source, int sourceOffset, int count) {
        logger.verbose("[%d] L1 CACHE: writeBlockRange block %d, offset %d, %d-byte source, src-offset %d, count %d",
                hartId, block, offset, source.length, sourceOffset, count);

        int toWait = 0;

        // Best-case scenario: local write is possible
        synchronized (localLock) {
            MESILine line = lines.read(block);

            if (line == null) logger.verbose("[%d] L1 CACHE: Fetched null from L1 cache for block %d", hartId, block);
            else {
                logger.verbose("[%d] L1 CACHE: Fetched line of block %d and state %s using block %d", hartId, line.blockAddress, line.state.name(), block);
            }

            if (isValid(line)
                    && (line.state == MESI.MODIFIED
                    || line.state == MESI.EXCLUSIVE)) {
                // Try a write-through, abort if stall requested
                if (!writeBack) {
                    logger.verbose("[%d] L1 CACHE: no writeBack, updating backing store", hartId);
                    byte[] newBlock = line.data.clone();
                    System.arraycopy(source, sourceOffset, newBlock, offset, count);
                    MemoryResult result = backingStore.write(block, newBlock);
                    toWait += result.shouldWait();
                    line.state = MESI.EXCLUSIVE;
                }

                System.arraycopy(source, sourceOffset, line.data, offset, count);
                line.touch(++clock);

                if (line.state == MESI.EXCLUSIVE && writeBack) {
                    line.state = MESI.MODIFIED;
                }

                return new MemoryResult(toWait, null);
            }
        }
        logger.verbose("[%d] L1 CACHE: Local write not possible", hartId);

        // Painful scenario: local write is not possible, MESI operation needed
        synchronized (this.getCoherencyLock(block)) {
            // Check again if another hart changed local cache while acquiring coherency lock
            synchronized (localLock) {
                MESILine line = lines.read(block);
                if (
                        isValid(line) &&
                                (line.state == MESI.MODIFIED || line.state == MESI.EXCLUSIVE))
                {
                    // Try a write-through, abort if stall requested
                    if (!writeBack) {
                        logger.verbose("[%d] L1 CACHE: no writeBack, updating backing store", hartId);
                        byte[] newBlock = line.data.clone();
                        System.arraycopy(source, sourceOffset, newBlock, offset, count);
                        MemoryResult result = backingStore.write(block, newBlock);
                        toWait += result.shouldWait();
                        line.state = MESI.EXCLUSIVE;
                    }

                    System.arraycopy(source, sourceOffset, line.data, offset, count);
                    line.touch(++clock);

                    if (line.state == MESI.EXCLUSIVE && writeBack) {
                        line.state = MESI.MODIFIED;
                    }

                    return new MemoryResult(toWait, null);
                }
            }
            logger.verbose("[%d] L1 CACHE: Local write STILL not possible", hartId);

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
                logger.verbose("[%d] L1 CACHE: Attempting to fetch line from peers", hartId);
                MemoryResult result = findPeerData(block);
                toWait += 1 + result.shouldWait();
                data = result.data();
            }
            logger.verbose("[%d] L1 CACHE: Finished peer fetch check.", hartId);

            // If no peer has the line AND IT IS NOT SHARED, access backing store
            if (data == null) {
                logger.verbose("[%d] L1 CACHE: Peer fetch attempt yielded nothing, reading from backing store...", hartId);
                MemoryResult result = backingStore.read(block, blockSize);
                toWait += 1 + result.shouldWait();
                data = result.data();
            }

            if (coherency != CacheCoherency.NONE) {
                invalidatePeerCopies(block);
            }

            // Finally, read to local cache and request stall
            synchronized (localLock) {
                logger.verbose("[%d] L1 CACHE: Finally installing line.", hartId);
                InstallationResult instResult = installLine(block, data, MESI.EXCLUSIVE);
                toWait += instResult.shouldWait();
                registerDirectoryLine(block, instResult.data());
                MESILine line = instResult.data();

                // Try a write-through if necessary, abort if stall requested
                if (!writeBack) {
                    logger.verbose("[%d] L1 CACHE: no writeBack, updating backing store", hartId);
                    byte[] newBlock = line.data.clone();
                    System.arraycopy(source, sourceOffset, newBlock, offset, count);
                    MemoryResult result = backingStore.write(block, newBlock);
                    logger.verbose("[%d] L1 CACHE: updated backing store for %d stall(s).", hartId, result.shouldWait());
                    toWait += result.shouldWait();
                    line.state = MESI.EXCLUSIVE;
                }

                System.arraycopy(source, sourceOffset, line.data, offset, count);
                line.touch(++clock);

                if (line.state == MESI.EXCLUSIVE && writeBack) {
                    line.state = MESI.MODIFIED;
                }

                logger.verbose("[%d] L1 CACHE: returning from writeBlockRange.", hartId);
                return new MemoryResult(toWait, null);
            }
        }
    }

    /**
     * Tries to find block amongst peers. Should only be called with coherency lock held.
     */
    private MemoryResult findPeerData(long block) {
        if (coherency == CacheCoherency.NONE) {
            return new MemoryResult(0, null);
        }

        int toWait = 0;

        for (L1Cache peer : peerCaches(block)) {
            synchronized (peer.localLock) {
                MESILine peerLine = peer.lines.read(block);
                if (!isValid(peerLine)) {
                    continue;
                }

                byte[] data = Arrays.copyOf(peerLine.data, blockSize);
                if (peerLine.state == MESI.MODIFIED) {
                    // If peer's line is dirty, try to write back and abort peer search if stall requested
                    MemoryResult result = peer.backingStore.write(block, peerLine.data);
                    toWait += result.shouldWait();

                    peerLine.state = MESI.SHARED;
                } else if (peerLine.state == MESI.EXCLUSIVE) {
                    peerLine.state = MESI.SHARED;
                }
                return new MemoryResult(toWait, data);
            }
        }

        return new MemoryResult(toWait, null);
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
     * Adds a line to the local cache. Should only be called with local lock held.
     */
    private InstallationResult installLine(long block, byte[] data, MESI state) {
        logger.debug("[%d] L1 CACHE: installLine block %d, %d bytes, %s", hartId, block, data.length, state.name());

        int toWait = 0;

        MESILine victim = this.lines.findVictim(block);
        if (victim != null && victim.state == MESI.MODIFIED) {
            logger.debug("[%d] L1 CACHE: Found MODIFIED victim of block %d using block %d", hartId, victim.blockAddress, block);
            // Attempt to write the dirty victim to backing store, abort installation if stall requested
            MemoryResult result = backingStore.write(victim.blockAddress, victim.data);
            toWait += result.shouldWait();

            // If write back worked, remove from local cache
            unregisterDirectoryLine(victim.blockAddress);
            this.lines.evictBlock(victim.blockAddress);
        }

        MESILine line = new MESILine(block, Arrays.copyOf(data, blockSize), state, ++clock);
        lines.write(line);
        line.touch(++clock);
        registerDirectoryLine(block, line);
        return new InstallationResult(toWait, line);
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
