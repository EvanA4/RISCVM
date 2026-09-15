package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.util.Nullable;
import net.eabbott.riscvm.util.VMLogger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.locks.ReentrantLock;

public class RandomAccessMemory {
    VMLogger logger = VMLogger.getInstance();
    Arena arena = Arena.ofShared();
    ReentrantLock lock = new ReentrantLock();
    MemorySegment segment;

    public RandomAccessMemory(long size) {
        this.segment = arena.allocate(size);
    }

    public boolean write(long address, byte[] src) {
        boolean output = false;
        lock.lock();
        try {
            MemorySegment asSegment = MemorySegment.ofArray(src);
            segment.asSlice(address, src.length).copyFrom(asSegment);
            output = true;
        } catch (Exception e) {
            logger.log("Failed to write %d bytes to RAM: %s", src.length, e.getMessage());
        } finally {
            lock.unlock();
        }
        return output;
    }

    public @Nullable byte[] read(long address, long size) {
        byte[] output = null;
        lock.lock();
        try {
            output = segment.asSlice(address, size).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Exception e) {
            logger.log("Failed to read %d bytes to RAM: %s", size, e.getMessage());
        } finally {
            lock.unlock();
        }
        return output;
    }
}
