package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.elf.ELFContext;
import net.eabbott.riscvm.context.elf.ELFProgramHeader;
import net.eabbott.riscvm.util.Nullable;
import net.eabbott.riscvm.util.VMLogger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.locks.ReentrantLock;

/*
* The virtual machine's RAM.
* */
public class RandomAccessMemory {
    VMLogger logger = VMLogger.getInstance();
    Arena arena = Arena.ofShared();
    ReentrantLock lock = new ReentrantLock();
    MemorySegment segment;

    public RandomAccessMemory(long size) {
        this.segment = arena.allocate(size);
    }

    /*
    * Fills memory with ELF file data.
    * */
    public boolean populate(ELFContext elf) {
        try {
            for (ELFProgramHeader ph : elf.programHeaders) {
                elf.reader.seek(ph.offset);
                byte[] toWrite = new byte[ph.filesz];
                elf.reader.read(toWrite);
                if (!this.write(ph.vaddr, toWrite)) return false;
                if (ph.memsz > ph.filesz && !this.zero(ph.vaddr + ph.filesz, ph.memsz - ph.filesz)) {
                    return false;
                }
            }

            return true;

        } catch (IOException e) {
            return false;
        }
    }

    /*
     * Writes src.length bytes at address in RAM.
     * */
    public boolean zero(long address, int size) {
        boolean output = false;
        lock.lock();
        try {
            MemorySegment asSegment = MemorySegment.ofArray(new byte[size]);
            segment.asSlice(address, size).copyFrom(asSegment);
            output = true;
        } catch (Exception e) {
            logger.log("Failed to zero %d bytes of RAM: %s", size, e.getMessage());
        } finally {
            lock.unlock();
        }
        return output;
    }

    /*
    * Writes src.length bytes at address in RAM.
    * */
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

    /*
     * Reads size bytes at address in RAM.
     * */
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
