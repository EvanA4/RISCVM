package net.eabbott.riscvm.machine.memory.cache;

import net.eabbott.riscvm.context.cache.CacheCoherency;
import net.eabbott.riscvm.context.cache.CacheContext;
import net.eabbott.riscvm.context.cache.CacheEvictionPolicy;
import net.eabbott.riscvm.machine.memory.BackingStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A private, block-based cache. Coherent caches must use the same coherency
 * mode and block size. The global lock serializes accesses and coherence
 * transitions across private caches.
 */
public final class L2Cache implements BackingStore {
    private static final Object BUS_LOCK = new Object();
    private static final Map<Integer, L2Cache> CACHES = new ConcurrentHashMap<>();

    private final int hardId;
    private final int blockSize;
    private final int capacityLines;
    private final boolean writeBack;
    private final BackingStore backingStore;
    private final CacheEvictionPolicy evictionPolicy;
    private final CacheCoherency coherency;

    // Insertion order is used for deterministic tie-breaking.
    private final Map<Long, L12Line> lines = new HashMap<>();

    // Per-block sharers tracked by the directory protocol.
    private static final Map<Long, Map<Integer, L12Line>> DIRECTORY =
            new HashMap<>();

    private long clock;

    public L2Cache(CacheCoherency coherency, CacheContext context, int hartId, BackingStore backingStore) {
        Objects.requireNonNull(context, "context");
        this.backingStore = Objects.requireNonNull(backingStore, "backingStore");

        if (context.blockSize <= 0 || context.size < context.blockSize) {
            throw new IllegalArgumentException("Cache size and block size must be positive.");
        }

        this.hardId = hartId;
        this.blockSize = context.blockSize;
        this.capacityLines = Math.toIntExact(context.size / context.blockSize);
        this.writeBack = context.isWriteBack;
        this.evictionPolicy = Objects.requireNonNull(context.eviction, "eviction");
        this.coherency = Objects.requireNonNull(coherency, "coherency");

        synchronized (BUS_LOCK) {
            L2Cache previous = CACHES.putIfAbsent(hardId, this);
            if (previous != null) {
                throw new IllegalStateException("Cache already registered for hart " + hardId);
            }
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

                L12Line line = getOrLoad(blockAddress);
                touch(line);
                System.arraycopy(line.data, offset, result, copied, count);
                copied += count;
            }
        }
        return result;
    }

    @Override
    public void write(long address, byte[] data) {
        Objects.requireNonNull(data, "data");
        checkRange(address, data.length);

        synchronized (BUS_LOCK) {
            int copied = 0;
            while (copied < data.length) {
                long currentAddress = address + copied;
                long blockAddress = blockAddress(currentAddress);
                int offset = (int) (currentAddress - blockAddress);
                int count = Math.min(data.length - copied, blockSize - offset);

                L12Line line = getOrLoad(blockAddress);
                touch(line);

                System.arraycopy(data, copied, line.data, offset, count);

                if (writeBack) {
                    invalidateOtherCopies(blockAddress);
                    line.state = MESI.MODIFIED;
                } else {
                    backingStore.write(blockAddress, line.data);
                    updateOtherCopies(blockAddress, line);
                }

                copied += count;
            }
        }
    }

    private L12Line getOrLoad(long blockAddress) {
        L12Line line = lines.get(blockAddress);
        if (line != null && line.state != MESI.INVALID) {
            return line;
        }

        evictIfFull();

        byte[] data = null;
        boolean shared = false;

        if (coherency == CacheCoherency.SNOOP) {
            // Snooping finds copies by examining the other private caches.
            for (L2Cache peer : CACHES.values()) {
                if (peer == this) continue;
                L12Line peerLine = peer.lines.get(blockAddress);
                if (peerLine == null || peerLine.state == MESI.INVALID) continue;

                if (data == null) data = Arrays.copyOf(peerLine.data, blockSize);
                shared = true;
                if (peerLine.state == MESI.MODIFIED || peerLine.state == MESI.EXCLUSIVE) {
                    peerLine.state = MESI.SHARED;
                }
            }
        } else if (coherency == CacheCoherency.DIR) {
            // Directory lookup finds only the caches registered as sharers.
            Map<Integer, L12Line> sharers = DIRECTORY.get(blockAddress);
            if (sharers != null) {
                for (Map.Entry<Integer, L12Line> entry : sharers.entrySet()) {
                    if (entry.getKey() == hardId) continue;
                    L12Line peerLine = entry.getValue();
                    if (peerLine == null || peerLine.state == MESI.INVALID) continue;

                    if (data == null) data = Arrays.copyOf(peerLine.data, blockSize);
                    shared = true;
                    if (peerLine.state == MESI.MODIFIED || peerLine.state == MESI.EXCLUSIVE) {
                        peerLine.state = MESI.SHARED;
                    }
                }
            }
        }

        if (data == null) {
            data = backingStore.read(blockAddress, blockSize);
            if (data == null || data.length != blockSize) {
                throw new IllegalStateException("Backing store returned an invalid cache block.");
            }
        }

        MESI state = shared ? MESI.SHARED : MESI.EXCLUSIVE;
        line = new L12Line(blockAddress, data, state, ++clock);
        lines.put(blockAddress, line);
        registerDirectoryLine(blockAddress, line);
        return line;
    }

    private void invalidateOtherCopies(long blockAddress) {
        if (coherency == CacheCoherency.NONE) return;

        if (coherency == CacheCoherency.SNOOP) {
            for (L2Cache peer : CACHES.values()) {
                if (peer != this) peer.invalidateLocal(blockAddress);
            }
        } else {
            Map<Integer, L12Line> sharers = DIRECTORY.get(blockAddress);
            if (sharers != null) {
                for (Map.Entry<Integer, L12Line> entry : sharers.entrySet()) {
                    if (entry.getKey() != hardId) {
                        L2Cache peer = CACHES.get(entry.getKey());
                        if (peer != null) peer.invalidateLocal(blockAddress);
                    }
                }
            }
        }

        registerDirectoryLine(blockAddress, lines.get(blockAddress));
    }

    private void invalidateLocal(long blockAddress) {
        L12Line line = lines.get(blockAddress);
        if (line != null) {
            line.state = MESI.INVALID;
            unregisterDirectoryLine(blockAddress);
        }
    }

    private void registerDirectoryLine(long blockAddress, L12Line line) {
        if (coherency != CacheCoherency.DIR || line == null) return;
        DIRECTORY.computeIfAbsent(blockAddress, ignored -> new HashMap<>())
                .put(hardId, line);
    }

    private void unregisterDirectoryLine(long blockAddress) {
        if (coherency != CacheCoherency.DIR) return;
        Map<Integer, L12Line> sharers = DIRECTORY.get(blockAddress);
        if (sharers != null) {
            sharers.remove(hardId);
            if (sharers.isEmpty()) DIRECTORY.remove(blockAddress);
        }
    }

    private void evictIfFull() {
        if (lines.size() < capacityLines) return;

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

        if (victim.state == MESI.MODIFIED) {
            backingStore.write(victim.blockAddress, victim.data);
        }
        unregisterDirectoryLine(victim.blockAddress);
        lines.remove(victim.blockAddress);
    }

    private boolean isEarlierVictim(L12Line candidate, L12Line current) {
        return switch (evictionPolicy) {
            case FIFO -> candidate.loadedAt < current.loadedAt;
            case LRU -> candidate.lastUsed < current.lastUsed;
            case LFU -> candidate.frequency < current.frequency;
        };
    }

    private void touch(L12Line line) {
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

    /** Call when a hart/cache is destroyed, to avoid retaining its cache. */
    public void close() {
        synchronized (BUS_LOCK) {
            for (L12Line line : lines.values()) {
                if (line.state == MESI.MODIFIED) {
                    backingStore.write(line.blockAddress, line.data);
                }
                unregisterDirectoryLine(line.blockAddress);
            }
            lines.clear();
            CACHES.remove(hardId, this);
        }
    }

    private void updateOtherCopies(long blockAddress, L12Line source) {
        if (coherency == CacheCoherency.NONE) {
            return;
        }

        if (coherency == CacheCoherency.SNOOP) {
            for (L2Cache peer : CACHES.values()) {
                if (peer != this) {
                    updatePeerLine(peer, blockAddress, source.data);
                }
            }
        } else if (coherency == CacheCoherency.DIR) {
            Map<Integer, L12Line> sharers = DIRECTORY.get(blockAddress);
            if (sharers != null) {
                for (Integer peerId : sharers.keySet()) {
                    if (peerId == hardId) {
                        continue;
                    }
                    L2Cache peer = CACHES.get(peerId);
                    if (peer != null) {
                        updatePeerLine(peer, blockAddress, source.data);
                    }
                }
            }
        }

        source.state = MESI.SHARED;
        registerDirectoryLine(blockAddress, source);
    }

    private void updatePeerLine(L2Cache peer, long blockAddress, byte[] data) {
        L12Line peerLine = peer.lines.get(blockAddress);
        if (peerLine != null && peerLine.state != MESI.INVALID) {
            System.arraycopy(data, 0, peerLine.data, 0, blockSize);
            peerLine.state = MESI.SHARED;
        }
    }

}
