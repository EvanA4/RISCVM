// package net.eabbott.riscvm.machine.memory;
//
// import java.util.Objects;
//
// public final class Cache {
//     public enum EvictionPolicy {
//         FIFO,
//         LRU,
//         LFU
//     }
//
//     public enum WritePolicy {
//         WRITE_THROUGH,
//         WRITE_BACK
//     }
//
//     public enum Associativity {
//         DIRECT_MAPPED,
//         SET_ASSOCIATIVE,
//         FULLY_ASSOCIATIVE
//     }
//
//     public enum MesiState {
//         INVALID,
//         SHARED,
//         EXCLUSIVE,
//         MODIFIED
//     }
//
//     /**
//      * The backing store can be RAM or another cache.
//      * Addresses passed here are physical byte addresses.
//      */
//     public interface BackingStore {
//         byte[] readBlock(long blockAddress, int blockSize);
//         void writeBlock(long blockAddress, byte[] data);
//     }
//
//     private static final class Line {
//         long tag;
//         byte[] data;
//         boolean valid;
//         boolean dirty;
//         long insertedAt;
//         long lastUsed;
//         long hitCount;
//         MesiState mesiState = MesiState.INVALID;
//     }
//
//     private final int capacityBytes;
//     private final int blockSize;
//     private final int ways;
//     private final int setCount;
//     private final Associativity associativity;
//     private final EvictionPolicy evictionPolicy;
//     private final WritePolicy writePolicy;
//     private final BackingStore backingStore;
//     private final Line[][] sets;
//
//     private long clock;
//
//     /**
//      * @param capacityBytes total cache capacity
//      * @param blockSize cache block size in bytes
//      * @param associativity cache organization
//      * @param waysExponent for set-associative caches, ways = 2^waysExponent;
//      *                     ignored for direct-mapped and fully-associative caches
//      */
//     public Cache(
//             int capacityBytes,
//             int blockSize,
//             Associativity associativity,
//             int waysExponent,
//             EvictionPolicy evictionPolicy,
//             WritePolicy writePolicy,
//             BackingStore backingStore) {
//
//         if (capacityBytes <= 0 || blockSize <= 0) {
//             throw new IllegalArgumentException("Cache size and block size must be positive");
//         }
//         if (capacityBytes % blockSize != 0) {
//             throw new IllegalArgumentException("Cache size must be a multiple of block size");
//         }
//
//         this.associativity = Objects.requireNonNull(associativity);
//         this.evictionPolicy = Objects.requireNonNull(evictionPolicy);
//         this.writePolicy = Objects.requireNonNull(writePolicy);
//         this.backingStore = Objects.requireNonNull(backingStore);
//
//         int lineCount = capacityBytes / blockSize;
//
//         switch (associativity) {
//             case DIRECT_MAPPED -> this.ways = 1;
//             case FULLY_ASSOCIATIVE -> this.ways = lineCount;
//             case SET_ASSOCIATIVE -> {
//                 if (waysExponent < 0 || waysExponent >= 31) {
//                     throw new IllegalArgumentException("Invalid ways exponent");
//                 }
//                 this.ways = 1 << waysExponent;
//                 if (this.ways > lineCount || lineCount % this.ways != 0) {
//                     throw new IllegalArgumentException(
//                             "Number of ways must divide the cache line count");
//                 }
//             }
//             default -> throw new IllegalArgumentException("Unsupported associativity");
//         }
//
//         this.capacityBytes = capacityBytes;
//         this.blockSize = blockSize;
//         this.setCount = lineCount / ways;
//         this.sets = new Line[setCount][ways];
//
//         for (int set = 0; set < setCount; set++) {
//             for (int way = 0; way < ways; way++) {
//                 sets[set][way] = new Line();
//             }
//         }
//     }
//
//     public synchronized int capacityBytes() {
//         return capacityBytes;
//     }
//
//     public synchronized int blockSize() {
//         return blockSize;
//     }
//
//     public synchronized int ways() {
//         return ways;
//     }
//
//     public synchronized int setCount() {
//         return setCount;
//     }
//
//     /**
//      * Reads length bytes, including reads that cross block boundaries.
//      */
//     public synchronized byte[] read(long address, int length) {
//         checkRange(address, length);
//
//         byte[] result = new byte[length];
//         int copied = 0;
//
//         while (copied < length) {
//             long currentAddress = address + copied;
//             int offset = (int) (currentAddress % blockSize);
//             int amount = Math.min(length - copied, blockSize - offset);
//
//             Line line = getLine(currentAddress);
//             System.arraycopy(line.data, offset, result, copied, amount);
//             copied += amount;
//         }
//
//         return result;
//     }
//
//     /**
//      * Writes bytes, including writes that cross block boundaries.
//      * This cache uses write-allocate: a write miss first loads the block.
//      */
//     public synchronized void write(long address, byte[] source) {
//         Objects.requireNonNull(source, "source");
//         checkRange(address, source.length);
//
//         int copied = 0;
//
//         while (copied < source.length) {
//             long currentAddress = address + copied;
//             int offset = (int) (currentAddress % blockSize);
//             int amount = Math.min(source.length - copied, blockSize - offset);
//
//             Line line = getLine(currentAddress);
//             System.arraycopy(source, copied, line.data, offset, amount);
//
//             if (writePolicy == WritePolicy.WRITE_BACK) {
//                 line.dirty = true;
//                 line.mesiState = MesiState.MODIFIED;
//             } else {
//                 long blockAddress = blockAddress(currentAddress);
//                 backingStore.writeBlock(blockAddress, line.data.clone());
//                 line.dirty = false;
//                 line.mesiState = MesiState.SHARED;
//             }
//
//             copied += amount;
//         }
//     }
//
//     /**
//      * Writes dirty lines to the backing store. Useful when flushing or shutting
//      * down the machine.
//      */
//     public synchronized void flush() {
//         for (int set = 0; set < setCount; set++) {
//             for (int way = 0; way < ways; way++) {
//                 Line line = sets[set][way];
//                 if (line.valid && line.dirty) {
//                     backingStore.writeBlock(
//                             reconstructBlockAddress(set, line.tag),
//                             line.data.clone());
//                     line.dirty = false;
//                 }
//             }
//         }
//     }
//
//     /**
//      * Invalidates every line. Dirty write-back lines are first written back.
//      */
//     public synchronized void invalidateAll() {
//         flush();
//
//         for (Line[] set : sets) {
//             for (Line line : set) {
//                 line.valid = false;
//                 line.dirty = false;
//                 line.mesiState = MesiState.INVALID;
//                 line.data = null;
//             }
//         }
//     }
//
//     private Line getLine(long address) {
//         long blockNumber = address / blockSize;
//         int setIndex = (int) (blockNumber % setCount);
//         long tag = blockNumber / setCount;
//
//         Line[] set = sets[setIndex];
//
//         for (Line line : set) {
//             if (line.valid && line.tag == tag) {
//                 line.lastUsed = ++clock;
//                 line.hitCount++;
//                 return line;
//             }
//         }
//
//         Line victim = chooseVictim(set);
//         evictIfNeeded(victim, setIndex);
//
//         long blockAddress = blockAddress(address);
//         byte[] block = backingStore.readBlock(blockAddress, blockSize);
//         if (block == null || block.length != blockSize) {
//             throw new IllegalStateException("Backing store returned an invalid block");
//         }
//
//         victim.tag = tag;
//         victim.data = block.clone();
//         victim.valid = true;
//         victim.dirty = false;
//         victim.insertedAt = ++clock;
//         victim.lastUsed = clock;
//         victim.hitCount = 0;
//         victim.mesiState = MesiState.EXCLUSIVE;
//
//         return victim;
//     }
//
//     private Line chooseVictim(Line[] set) {
//         for (Line line : set) {
//             if (!line.valid) {
//                 return line;
//             }
//         }
//
//         Line victim = set[0];
//         for (int i = 1; i < set.length; i++) {
//             Line candidate = set[i];
//
//             boolean replace = switch (evictionPolicy) {
//                 case FIFO -> candidate.insertedAt < victim.insertedAt;
//                 case LRU -> candidate.lastUsed < victim.lastUsed;
//                 case LFU -> candidate.hitCount < victim.hitCount;
//             };
//
//             if (replace) {
//                 victim = candidate;
//             }
//         }
//
//         return victim;
//     }
//
//     private void evictIfNeeded(Line victim, int setIndex) {
//         if (victim.valid && victim.dirty) {
//             backingStore.writeBlock(
//                     reconstructBlockAddress(setIndex, victim.tag),
//                     victim.data.clone());
//         }
//     }
//
//     private long blockAddress(long address) {
//         return address - (address % blockSize);
//     }
//
//     private long reconstructBlockAddress(int setIndex, long tag) {
//         long blockNumber = tag * setCount + setIndex;
//         return blockNumber * blockSize;
//     }
//
//     private static void checkRange(long address, int length) {
//         if (address < 0 || length < 0 || address > Long.MAX_VALUE - length) {
//             throw new IllegalArgumentException("Invalid address range");
//         }
//     }
// }
