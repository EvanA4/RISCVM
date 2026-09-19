package net.eabbott.riscvm.util;

import java.io.FileWriter;
import java.io.IOException;

/*
* The logger for the entire virtual machine application.
* */
public class VMLogger {
    private static VMLogger instance;
    private @Nullable FileWriter writer = null;

    public VMLogger() {
        instance = this;
    }

    /*
    * A cheeky function allowing for a global VMLogger object.
    * */
    public static VMLogger getInstance() {
        return instance;
    }

    /*
    * Only called if meant to log to an actual file.
    * */
    public void open(String fileName) {
        try {
            this.writer = new FileWriter(fileName);
        } catch (IOException e) {
            throw new RuntimeException(
                String.format("Failed to open output file: %s", e.getMessage())
            );
        }
    }

    /*
    * A basic string formatting log function. Logs to the correct location.
    * */
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

    /*
    * Helpful binary logging for debugging.
    * */
    public void logBytes(byte[] src) {
        this.logBytes(src, 4, false);
    }

    /*
     * Helpful binary logging for debugging.
     * */
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

    /*
     * Converts a byte into a series of 1's and 0's.
     * */
    private String stringifyByte(byte src, boolean reverse) {
        int unsignedByte = src & 0xFF;
        String binary = Integer.toBinaryString(unsignedByte);
        StringBuilder stringBuilder = new StringBuilder(
            String.format("%8s", binary).replace(' ', '0')
        );
        return reverse ? stringBuilder.toString() : stringBuilder.reverse().toString();
    }
}