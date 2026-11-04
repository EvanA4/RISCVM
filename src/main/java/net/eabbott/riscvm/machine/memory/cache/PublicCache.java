package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;
import net.eabbott.riscvm.machine.memory.BackingStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A block-based cache shared by all harts. It has no cache-coherence protocol.
 */
public final class PublicCache implements BackingStore {
    private final int blockSize;
    private final int capacityLines;
    private final BackingStore backingStore;
    private final CacheEvictionPolicy evictionPolicy;
    private final boolean writeBack;

    private final Map<Long, SharedCacheLine> lines = new HashMap<>();
    private long clock;

    public PublicCache(CacheContext context, BackingStore backingStore) {
        Objects.requireNonNull(context, "context");
        this.backingStore = Objects.requireNonNull(backingStore, "backingStore");

        if (context.blockSize <= 0 || context.size < context.blockSize) {
            throw new IllegalArgumentException(
                    "Cache size and block size must be positive.");
        }

        this.blockSize = context.blockSize;
        this.capacityLines = Math.toIntExact(context.size / context.blockSize);
        this.evictionPolicy = Objects.requireNonNull(context.eviction, "eviction");
        this.writeBack = context.isWriteBack;
    }

    @Override
    public synchronized byte[] read(long address, int size) {
        checkRange(address, size);
        byte[] result = new byte[size];

        int copied = 0;
        while (copied < size) {
            long currentAddress = address + copied;
            long blockAddress = blockAddress(currentAddress);
            int offset = (int) (currentAddress - blockAddress);
            int count = Math.min(size - copied, blockSize - offset);

            SharedCacheLine line = getOrLoad(blockAddress);
            touch(line);
            System.arraycopy(line.data, offset, result, copied, count);
            copied += count;
        }

        return result;
    }

    @Override
    public synchronized void write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");
        checkRange(address, data.length);

        int copied = 0;
        while (copied < data.length) {
            long currentAddress = address + copied;
            long blockAddress = blockAddress(currentAddress);
            int offset = (int) (currentAddress - blockAddress);
            int count = Math.min(data.length - copied, blockSize - offset);

            SharedCacheLine line = getOrLoad(blockAddress);
            touch(line);
            System.arraycopy(data, copied, line.data, offset, count);

            if (writeBack) {
                line.dirty = true;
            } else {
                backingStore.write(blockAddress, line.data);
                line.dirty = false;
            }

            copied += count;
        }
    }

    private SharedCacheLine getOrLoad(long blockAddress) {
        SharedCacheLine line = lines.get(blockAddress);
        if (line != null) {
            return line;
        }

        evictIfFull();

        byte[] data = backingStore.read(blockAddress, blockSize);
        if (data == null || data.length != blockSize) {
            throw new IllegalStateException(
                    "Backing store returned an invalid cache block.");
        }

        line = new SharedCacheLine(blockAddress, data, ++clock);
        lines.put(blockAddress, line);
        return line;
    }

    private void evictIfFull() {
        if (lines.size() < capacityLines) {
            return;
        }

        SharedCacheLine victim = null;
        for (SharedCacheLine candidate : lines.values()) {
            if (victim == null || isEarlierVictim(candidate, victim)) {
                victim = candidate;
            }
        }

        if (victim == null) {
            throw new IllegalStateException("Cache has no evictable line.");
        }

        writeBackIfDirty(victim);
        lines.remove(victim.blockAddress);
    }

    private void writeBackIfDirty(SharedCacheLine line) {
        if (line.dirty) {
            backingStore.write(line.blockAddress, line.data);
            line.dirty = false;
        }
    }

    private boolean isEarlierVictim(
            SharedCacheLine candidate, SharedCacheLine current) {
        return switch (evictionPolicy) {
            case FIFO -> candidate.loadedAt < current.loadedAt;
            case LRU -> candidate.lastUsed < current.lastUsed;
            case LFU -> candidate.frequency < current.frequency;
        };
    }

    private void touch(SharedCacheLine line) {
        line.frequency++;
        line.lastUsed = ++clock;
    }

    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }

    private static void checkRange(long address, int size) {
        if (address < 0 || size < 0 || address > Long.MAX_VALUE - size) {
            throw new IllegalArgumentException("Invalid memory range.");
        }
    }
}
