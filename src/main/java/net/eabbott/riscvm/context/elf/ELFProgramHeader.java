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

        programHeader.type = BinaryUtil.intFromBytes(rawHeader, 0, 4);
        programHeader.offset = BinaryUtil.intFromBytes(rawHeader, 4, 4);
        programHeader.vaddr = BinaryUtil.intFromBytes(rawHeader, 8, 4);
        programHeader.paddr = BinaryUtil.intFromBytes(rawHeader, 12, 4);
        programHeader.filesz = BinaryUtil.intFromBytes(rawHeader, 16, 4);
        programHeader.memsz = BinaryUtil.intFromBytes(rawHeader, 20, 4);
        programHeader.flags = BinaryUtil.intFromBytes(rawHeader, 24, 4);
        programHeader.align = BinaryUtil.intFromBytes(rawHeader, 28, 4);

        return programHeader;
    }
}
