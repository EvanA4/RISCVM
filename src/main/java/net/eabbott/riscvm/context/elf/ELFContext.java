package net.eabbott.riscvm.context.elf;

import net.eabbott.riscvm.util.VMLogger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Vector;

public class ELFContext {
    public ELFHeader header;
    public List<ELFProgramHeader> programHeaders;
    public RandomAccessFile reader;

    public static ELFContext read(String elfFile) throws IOException {
        ELFContext context = new ELFContext();
        context.reader = new RandomAccessFile(elfFile, "r");

        // Read and validate ELF header
        context.header = ELFHeader.read(context.reader);
        if (
            context.header.ident != 0x464c457f ||
            context.header.type != 2 ||
            context.header.machine != 243 ||
            context.header.bitsize != 1
        ) {
            throw new IllegalArgumentException(
                "Invalid ELF header."
            );
        }

        // Read and save loadable, ELF program headers
        context.programHeaders = new Vector<>();
        for (int i = 0; i < context.header.phnum; ++i) {
            int offset = i*32 + context.header.phoff;
            ELFProgramHeader programHeader = ELFProgramHeader.read(context.reader, offset);
            if (programHeader.type == 1) context.programHeaders.add(programHeader);
        }

        return context;
    }
}
