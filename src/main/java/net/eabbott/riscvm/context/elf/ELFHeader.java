package net.eabbott.riscvm.context.elf;

import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ELFHeader {
    public int ident;
    public int bitsize;
    public int endian;
    public int filever;
    public int osabi;
    public int abiver;
    public long padding;
    public int type;
    public int machine;
    public int version;
    public int entry;
    public int phoff;
    public int shoff;
    public int flags;
    public int ehsize;
    public int phentsize;
    public int phnum;
    public int shentsize;
    public int shnum;
    public int shstrndx;

    public static ELFHeader read(RandomAccessFile reader) throws IOException {
        ELFHeader header = new ELFHeader();
        byte[] rawHeader = new byte[52];
        reader.read(rawHeader);

//        for (int i = 0; i < rawHeader.length; ++i) VMLogger.getInstance().log("[%d] %x", i, rawHeader[i]);

        header.ident = BinaryUtil.intFromBytes(rawHeader, 0, 4);
        header.bitsize = BinaryUtil.intFromBytes(rawHeader, 4, 1);
        header.endian = BinaryUtil.intFromBytes(rawHeader, 5, 1);
        header.filever = BinaryUtil.intFromBytes(rawHeader, 6, 1);
        header.osabi = BinaryUtil.intFromBytes(rawHeader, 7, 1);
        header.abiver = BinaryUtil.intFromBytes(rawHeader, 8, 1);
        header.padding = BinaryUtil.longFromBytes(rawHeader, 9, 7);
        header.type = BinaryUtil.intFromBytes(rawHeader, 16, 2);
        header.machine = BinaryUtil.intFromBytes(rawHeader, 18, 2);
        header.version = BinaryUtil.intFromBytes(rawHeader, 20, 4);
        header.entry = BinaryUtil.intFromBytes(rawHeader, 24, 4);
        header.phoff = BinaryUtil.intFromBytes(rawHeader, 28, 4);
        header.shoff = BinaryUtil.intFromBytes(rawHeader, 32, 4);
        header.flags = BinaryUtil.intFromBytes(rawHeader, 36, 4);
        header.ehsize = BinaryUtil.intFromBytes(rawHeader, 40, 2);
        header.phentsize = BinaryUtil.intFromBytes(rawHeader, 42, 2);
        header.phnum = BinaryUtil.intFromBytes(rawHeader, 44, 2);
        header.shentsize = BinaryUtil.intFromBytes(rawHeader, 46, 2);
        header.shnum = BinaryUtil.intFromBytes(rawHeader, 48, 2);
        header.shstrndx = BinaryUtil.intFromBytes(rawHeader, 50, 2);

        return header;
    }
}
