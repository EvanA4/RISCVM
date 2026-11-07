package net.eabbott.riscvm.context.elf;

import net.eabbott.riscvm.util.BinaryUtil;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ELFProgramHeader {
    public int type;
    public int offset;
    public int vaddr;
    public int paddr;
    public int filesz;
    public int memsz;
    public int flags;
    public int align;

    public static ELFProgramHeader read(RandomAccessFile reader, int offset) throws IOException {
        ELFProgramHeader programHeader = new ELFProgramHeader();
        byte[] rawHeader = new byte[32];
        reader.seek(offset);
        reader.read(rawHeader);

        programHeader.type = BinaryUtil.intFromBytesLE(rawHeader, 0, 4);
        programHeader.offset = BinaryUtil.intFromBytesLE(rawHeader, 4, 4);
        programHeader.vaddr = BinaryUtil.intFromBytesLE(rawHeader, 8, 4);
        programHeader.paddr = BinaryUtil.intFromBytesLE(rawHeader, 12, 4);
        programHeader.filesz = BinaryUtil.intFromBytesLE(rawHeader, 16, 4);
        programHeader.memsz = BinaryUtil.intFromBytesLE(rawHeader, 20, 4);
        programHeader.flags = BinaryUtil.intFromBytesLE(rawHeader, 24, 4);
        programHeader.align = BinaryUtil.intFromBytesLE(rawHeader, 28, 4);

        return programHeader;
    }
}
