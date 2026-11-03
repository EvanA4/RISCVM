package net.eabbott.riscvm.machine.memory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A private, write-back, write-allocate L1 cache with snooping MESI coherence.
 *
 * Assumptions:
 * - All PrivateCache instances use the same block size.
 * - Each instance is registered under a unique hart ID.
 * - Its backing store is that hart's L2 cache.
 */
public final class PrivateCache implements BackingStore {
    public enum State {
        INVALID, SHARED, EXCLUSIVE, MODIFIED
    }

    private static final Object BUS_LOCK = new Object();
    private static final ConcurrentMap<Integer, PrivateCache> CACHES =
            new ConcurrentHashMap<>();

    private static final class Line {
        final long blockAddress;
        final byte[] data;
        State state;
        long frequency;
        long loadedAt;

        Line(long blockAddress, byte[] data, State state, long loadedAt) {
            this.blockAddress = blockAddress;
            this.data = data;
            this.state = state;
            this.frequency = 1;
            this.loadedAt = loadedAt;
        }
    }

    private final int hartId;
    private final int blockSize;
    private final int capacityLines;
    private final BackingStore lowerLevel;
    private final Map<Long, Line> lines = new HashMap<>();

    private long loadClock;

    public PrivateCache(
            int hartId,
            int capacityBytes,
            int blockSize,
            BackingStore lowerLevel
    ) {
        if (capacityBytes <= 0 || blockSize <= 0
                || capacityBytes % blockSize != 0) {
            throw new IllegalArgumentException(
                    "Capacity must be positive and divisible by block size");
        }

        this.hartId = hartId;
        this.blockSize = blockSize;
        this.capacityLines = capacityBytes / blockSize;
        this.lowerLevel = lowerLevel;

        PrivateCache previous = CACHES.putIfAbsent(hartId, this);
        if (previous != null) {
            throw new IllegalStateException(
                    "A private cache is already registered for hart " + hartId);
        }
    }

    @Override
    public byte[] read(long address, int size) {
        checkRange(address, size);
        byte[] result = new byte[size];

        synchronized (BUS_LOCK) {
            int copied = 0;
            while (copied < size) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(size - copied, blockSize - offset);

                Line line = getOrLoad(blockAddress);
                line.frequency++;
                System.arraycopy(line.data, offset, result, copied, count);
                copied += count;
            }
        }

        return result;
    }

    @Override
    public void write(long address, byte[] data) {
        checkRange(address, data.length);

        synchronized (BUS_LOCK) {
            int copied = 0;
            while (copied < data.length) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(data.length - copied, blockSize - offset);

                Line line = getOrLoad(blockAddress);
                line.frequency++;

                // An exclusive or modified line can be written without
                // invalidating another cache; otherwise issue an invalidate.
                if (line.state != State.EXCLUSIVE
                        && line.state != State.MODIFIED) {
                    invalidatePeers(blockAddress);
                }
                line.state = State.MODIFIED;

                System.arraycopy(data, copied, line.data, offset, count);
                copied += count;
            }
        }
    }

    private Line getOrLoad(long blockAddress) {
        Line existing = lines.get(blockAddress);
        if (existing != null && existing.state != State.INVALID) {
            return existing;
        }

        evictIfFull();

        byte[] data = null;
        boolean anotherCopyExists = false;

        // Snoop peers before fetching from the lower level. A peer with a
        // modified line supplies the newest data and downgrades to SHARED.
        for (PrivateCache peer : CACHES.values()) {
            if (peer == this) {
                continue;
            }

            Line peerLine = peer.lines.get(blockAddress);
            if (peerLine == null || peerLine.state == State.INVALID) {
                continue;
            }

            anotherCopyExists = true;
            if (data == null) {
                data = Arrays.copyOf(peerLine.data, blockSize);
            }
            if (peerLine.state == State.MODIFIED
                    || peerLine.state == State.EXCLUSIVE) {
                peerLine.state = State.SHARED;
            }
        }

        if (data == null) {
            data = lowerLevel.read(blockAddress, blockSize);
        }

        State state = anotherCopyExists ? State.SHARED : State.EXCLUSIVE;
        Line loaded = new Line(blockAddress, data, state, ++loadClock);
        lines.put(blockAddress, loaded);
        return loaded;
    }

    private void invalidatePeers(long blockAddress) {
        for (PrivateCache peer : CACHES.values()) {
            if (peer == this) {
                continue;
            }

            Line peerLine = peer.lines.get(blockAddress);
            if (peerLine != null) {
                // This cache has already obtained the current block contents.
                // Remove peer copies so this cache can transition to MODIFIED.
                peerLine.state = State.INVALID;
                peer.lines.remove(blockAddress);
            }
        }
    }

    private void evictIfFull() {
        if (lines.size() < capacityLines) {
            return;
        }

        Line victim = lines.values().stream()
                .min((a, b) -> {
                    int byFrequency = Long.compare(a.frequency, b.frequency);
                    return byFrequency != 0
                            ? byFrequency
                            : Long.compare(a.loadedAt, b.loadedAt);
                })
                .orElseThrow();

        if (victim.state == State.MODIFIED) {
            lowerLevel.write(victim.blockAddress, victim.data);
        }

        lines.remove(victim.blockAddress);
    }

    private long blockAddress(long address) {
        return address - Math.floorMod(address, blockSize);
    }

    private static void checkRange(long address, int size) {
        if (address < 0 || size < 0 || address > Long.MAX_VALUE - size) {
            throw new IllegalArgumentException("Invalid memory range");
        }
    }

    public State getState(long address) {
        synchronized (BUS_LOCK) {
            Line line = lines.get(blockAddress(address));
            return line == null ? State.INVALID : line.state;
        }
    }

    public static void unregisterHart(int hartId) {
        synchronized (BUS_LOCK) {
            CACHES.remove(hartId);
        }
    }

    public static void clearRegistry() {
        synchronized (BUS_LOCK) {
            CACHES.clear();
        }
    }
}
