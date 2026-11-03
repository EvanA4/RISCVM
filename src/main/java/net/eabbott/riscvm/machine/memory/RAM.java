package net.eabbott.riscvm.machine.memory;

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
public class RAM implements BackingStore {
    VMLogger logger = VMLogger.getInstance();
    Arena arena = Arena.ofShared();
    ReentrantLock lock = new ReentrantLock();
    MemorySegment segment;
    long size;

    public RAM(long size) {
        this.size = size;
        this.segment = arena.allocate(size);
    }

    /*
    * Returns the size of the RAM.
    * */
    public long length() {
        return this.size;
    }

    /*
    * Fills memory with ELF file data.
    * */
    public void populate(ELFContext elf) throws IOException {
        for (ELFProgramHeader ph : elf.programHeaders) {
            elf.reader.seek(ph.offset);
            byte[] toWrite = new byte[ph.filesz];
            elf.reader.read(toWrite);
            this.write(ph.vaddr, toWrite);
            if (ph.memsz > ph.filesz) this.zero(ph.vaddr + ph.filesz, ph.memsz - ph.filesz);
        }
    }

    /*
     * Writes src.length bytes at address in RAM.
     * */
    public synchronized void zero(long address, int size) {
        MemorySegment asSegment = MemorySegment.ofArray(new byte[size]);
        segment.asSlice(address, size).copyFrom(asSegment);
    }

    /*
    * Writes src.length bytes at address in RAM.
    * */
    public synchronized void write(long address, byte[] src) {
        MemorySegment asSegment = MemorySegment.ofArray(src);
        segment.asSlice(address, src.length).copyFrom(asSegment);
    }

    /*
     * Reads size bytes at address in RAM.
     * */
    public synchronized @Nullable byte[] read(long address, int size) {
        return segment.asSlice(address, size).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
    }
}
