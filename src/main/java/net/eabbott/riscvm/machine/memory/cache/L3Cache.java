package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;
import net.eabbott.riscvm.machine.memory.BackingStore;
import net.eabbott.riscvm.util.VMLogger;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A block-based cache shared by all harts. It has no cache-coherence protocol.
 */
public final class L3Cache implements BackingStore {
    private VMLogger logger = VMLogger.getInstance();
    private record FetchResult(int shouldWait, SimpleLine data){}
    private static final ReentrantLock LOCK = new ReentrantLock();
    
    private final int blockSize;
    private final BackingStore backingStore;
    private final boolean writeBack;

    private final SimpleCacheData lines;
    private long clock;

    public L3Cache(CacheContext context, BackingStore backingStore) {
        Objects.requireNonNull(context, "context");
        this.backingStore = Objects.requireNonNull(backingStore, "backingStore");

        if (context.blockSize <= 0 || context.size < context.blockSize) {
            throw new IllegalArgumentException(
                "Cache size and block size must be positive.");
        }

        this.blockSize = 1 << context.blockSize;
        this.writeBack = context.isWriteBack;
        this.lines = new SimpleCacheData(context);
    }

    // Reads from memory
    @Override
    public MemoryResult read(long address, int size) {
        LOCK.lock();
        try {
            byte[] output = new byte[size];
            int toWait = LOCK.getQueueLength();

            int copied = 0;
            while (copied < size) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(size - copied, blockSize - offset);

                MemoryResult result = readBlockRange(blockAddress, offset, count);
                toWait += result.shouldWait();

                System.arraycopy(result.data(), 0, output, copied, count);
                copied += count;
            }

            return new MemoryResult(toWait, output);
        } finally {
            LOCK.unlock();
        }
    }

    // Writes to memory
    @Override
    public MemoryResult write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");

        logger.verbose("L3 CACHE: trying to acquire static lock.");
        LOCK.lock();
        logger.verbose("L3 CACHE: acquired static lock.");
        try {
            int toWait = LOCK.getQueueLength();

            int copied = 0;
            while (copied < data.length) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(data.length - copied, blockSize - offset);

                MemoryResult result = writeBlockRange(blockAddress, offset, data, copied, count);
                toWait += result.shouldWait();

                copied += count;
            }

            logger.verbose("L3 CACHE: finished write and requesting %d stall(s).", toWait);
            return new MemoryResult(toWait, null);

        } finally {
            logger.verbose("L3 CACHE: trying to unlock.");
            LOCK.unlock();
            logger.verbose("L3 CACHE: finished unlocking.");
        }
    }

    // Reads a particular block from memory, passing only the relevant section to the "read" call
    private MemoryResult readBlockRange(long blockAddress, int offset, int count) {
        int toWait = 0;

        // Attempt to read locally
        SimpleLine line = lines.read(blockAddress);

        // Can't read locally, read from backing store :(
        if (line == null) {
            FetchResult result = fetchBackingStore(blockAddress);
            toWait += 1 + result.shouldWait();
            line = result.data();
        }

        // Actually read locally
        line.touch(++clock);
        byte[] range = Arrays.copyOfRange(line.data, offset, offset + count);
        return new MemoryResult(toWait, range);
    }

    // Updates a block in memory
    private MemoryResult writeBlockRange(long blockAddress, int offset, byte[] source, int sourceOffset, int count) {
        int toWait = 0;

        // Attempt to write locally
        SimpleLine line = lines.read(blockAddress);

        // Cannot write locally, read value and add to stall counter
        if (line == null) {
            FetchResult result = fetchBackingStore(blockAddress);
            toWait = 1 + result.shouldWait();
            line = result.data();
        }

        // Attempt write-through before writing to local cache, abort if process fails
        if (!writeBack) {
            byte[] newBlock = line.data.clone();
            System.arraycopy(source, sourceOffset, newBlock, offset, count);
            MemoryResult result = backingStore.write(line.blockAddress, newBlock);
            toWait += result.shouldWait();
        }

        System.arraycopy(source, sourceOffset, line.data, offset, count);
        if (writeBack) line.dirty = true;
        return new MemoryResult(toWait, null);
    }

    // Reads from the backing store, saving it into the local cache
    private FetchResult fetchBackingStore(long blockAddress) {
        int toWait = 0;

        // Evict if necessary
        SimpleLine victim = this.lines.findVictim(blockAddress);
        if (victim != null && victim.dirty) {
            // Try to write the dirty victim to backing store, abort process if stall requested
            MemoryResult result = backingStore.write(victim.blockAddress, victim.data);
            toWait += result.shouldWait();

            // If no stall requested, remove now-clean victim from cache
            this.lines.evictBlock(victim.blockAddress);
        }

        // Then read from backing store
        MemoryResult result = backingStore.read(blockAddress, blockSize);
        toWait += result.shouldWait();

        // Finally, update local cache and request stall
        SimpleLine line = new SimpleLine(blockAddress, result.data(), ++clock);
        lines.write(line);

        return new FetchResult(toWait, line);
    }

    // Converts an address into the starting address of its corresponding block
    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }
}
