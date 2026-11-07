package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;
import net.eabbott.riscvm.machine.memory.BackingStore;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A block-based cache shared by all harts. It has no cache-coherence protocol.
 */
public final class L3Cache implements BackingStore {
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

        this.blockSize = context.blockSize;
        this.writeBack = context.isWriteBack;
        this.lines = new SimpleCacheData(context);
    }

    @Override
    public MemoryResult read(long address, int size) {
        if (LOCK.tryLock()) {
            byte[] output = new byte[size];
    
            int copied = 0;
            while (copied < size) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(size - copied, blockSize - offset);
    
                MemoryResult result = readBlockRange(blockAddress, offset, count);
                if (result.shouldWait()) return new MemoryResult(true, null);

                System.arraycopy(result.data(), 0, output, copied, count);
                copied += count;
            }
    
            return new MemoryResult(false, output);
        }
        return new MemoryResult(true, null);
    }

    @Override
    public synchronized MemoryResult write(long address, byte[] data) {
        if (LOCK.tryLock()) {
            Objects.requireNonNull(data, "data");

            int copied = 0;
            while (copied < data.length) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(data.length - copied, blockSize - offset);

                MemoryResult result = writeBlockRange(blockAddress, offset, data, copied, count);
                if (result.shouldWait()) return new MemoryResult(true, null);

                copied += count;
            }
            return new MemoryResult(false, null);
        }
        return new MemoryResult(true, null);
    }

    private MemoryResult readBlockRange(long blockAddress, int offset, int count) {
        // Attempt to read locally
        SimpleLine line = lines.read(blockAddress);
        if (line != null) {
            line.touch(++clock);
            byte[] range = Arrays.copyOfRange(line.data, offset, offset + count);
            return new MemoryResult(false, range);
        }

        // Can't write locally, read from backing store :(
        fetchBackingStore(blockAddress);
        return new MemoryResult(true, null);
    }

    private MemoryResult writeBlockRange(long blockAddress, int offset, byte[] source, int sourceOffset, int count) {
        // Attempt to write locally
        SimpleLine line = lines.read(blockAddress);
        if (line != null) {
            line.touch(++clock);

            // Attempt write-through before writing to local cache, abort if process fails
            if (!writeBack) {
                byte[] newBlock = line.data.clone();
                System.arraycopy(source, sourceOffset, newBlock, offset, count);
                MemoryResult result = backingStore.write(line.blockAddress, newBlock);
                if (result.shouldWait()) return new MemoryResult(true, null);
            }

            System.arraycopy(source, sourceOffset, line.data, offset, count);
            return new MemoryResult(false, null);
        }

        // Can't write locally, read from backing store :(
        fetchBackingStore(blockAddress);
        return new MemoryResult(true, null);
    }

    private void fetchBackingStore(long blockAddress) {
        // Evict if necessary
        SimpleLine victim = this.lines.findVictim(blockAddress);
        if (victim != null && victim.dirty) {
            // Try to write the dirty victim to backing store, abort process if stall requested
            MemoryResult result = backingStore.write(victim.blockAddress, victim.data);
            if (result.shouldWait()) return;

            // If no stall requested, remove now-clean victim from cache
            this.lines.evictBlock(victim.blockAddress);
        }

        // Then read from backing store
        MemoryResult result = backingStore.read(blockAddress, blockSize);
        if (result.shouldWait()) return;

        // Finally, update local cache and request stall
        SimpleLine line = new SimpleLine(blockAddress, result.data(), ++clock);
        lines.write(line);
    }

    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }
}
