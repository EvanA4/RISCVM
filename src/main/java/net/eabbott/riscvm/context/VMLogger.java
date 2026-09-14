package net.eabbott.riscvm.context;

import net.eabbott.riscvm.util.Nullable;

import java.io.FileWriter;
import java.io.IOException;

public class VMLogger {
    @Nullable String fileName = null;
    @Nullable FileWriter writer = null;

    public void open(String fileName) {
        try {
            this.writer = new FileWriter(fileName);
            this.fileName = fileName;
        } catch (IOException e) {
            throw new RuntimeException(
                String.format("Failed to open output file: %s", e.getMessage())
            );
        }
    }

    public void log(String format, Object... args) {
        String text = String.format(format, args);
        if (this.writer != null) {
            try {
                this.writer.write(text + '\n');
            } catch (IOException e) {
                throw new RuntimeException(
                    String.format("Failed to write message to output: %s", e.getMessage())
                );
            }
        } else {
            IO.println(text);
        }
    }

    public void logBytes(byte[] src, int bytesPerLine, boolean reverse) {
        for (int offset = 0; offset < src.length; offset += bytesPerLine) {
            int end = Math.min(offset + bytesPerLine, src.length);
            StringBuilder line = new StringBuilder();
            if (reverse) {
                for (int i = end - 1; i >= offset; i--) {
                    if (!line.isEmpty()) line.append(" ");
                    line.append(stringifyByte(src[i], reverse));
                }
            } else {
                for (int i = offset; i < end; i++) {
                    if (!line.isEmpty()) line.append(" ");
                    line.append(stringifyByte(src[i], reverse));
                }
            }
            log(line.toString());
        }
    }

    private String stringifyByte(byte src, boolean reverse) {
        int unsignedByte = src & 0xFF;
        String binary = Integer.toBinaryString(unsignedByte);
        StringBuilder stringBuilder = new StringBuilder(
            String.format("%8s", binary).replace(' ', '0')
        );
        return reverse ? stringBuilder.toString() : stringBuilder.reverse().toString();
    }

    void logBytes(byte[] src) {
        this.logBytes(src, 4, false);
    }
}