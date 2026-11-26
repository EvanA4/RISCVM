package net.eabbott.riscvm.util;

import net.eabbott.riscvm.context.LogType;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
* The logger for the entire virtual machine application.
* */
public class VMLogger {
    private static VMLogger instance;
    private FileWriter writer;
    private LogType minLevel;
    private ReentrantLock lock;

    /**
    * A cheeky function allowing for a global VMLogger object.
    * */
    public static VMLogger getInstance() {
        return instance;
    }

    /**
     * Creates the global instance with a minimum logging level filter
     */
    public synchronized static void create(LogType minLevel) {
        instance = new VMLogger();
        instance.minLevel = minLevel;
        instance.lock = new ReentrantLock();
        if (minLevel == LogType.SILENT) return;

        DateTimeFormatter filenameTime =
                DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
                        .withZone(ZoneOffset.UTC);

        String fileName = "app-" + filenameTime.format(Instant.now()) + ".log";

        try {
            instance.writer = new FileWriter(fileName);
        } catch (IOException e) {
            throw new RuntimeException(
                String.format("Failed to open output file: %s", e.getMessage())
            );
        }
    }

    /**
     * A basic string formatting log function. Logs to the correct location.
     * */
    private void log(LogType logType, String format, Object... args) {
        if (logType.isGTE(this.minLevel)) {
            lock.lock();
            try {
                String text = String.format(format, args);
                if (this.writer != null) {
                    try {
                        this.writer.write(text + '\n');
                        this.writer.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(
                                String.format("Failed to write message to output: %s", e.getMessage())
                        );
                    }
                } else {
                    IO.println(text);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    // Logger functions for each logging level...

    public void verbose(String format, Object... args) {
        log(LogType.VERBOSE, "[VERBOSE] " + format, args);
    }

    public void debug(String format, Object... args) {
        log(LogType.DEBUG, "[DEBUG] " + format, args);
    }

    public void info(String format, Object... args) {
        log(LogType.INFO, "[INFO] " + format, args);
    }

    public void warning(String format, Object... args) {
        log(LogType.WARNING, "[WARNING] " + format, args);
    }

    public void error(String format, Object... args) {
        log(LogType.ERROR, "[ERROR] " + format, args);
    }

    public void fatal(String format, Object... args) {
        log(LogType.FATAL, "[FATAL] " + format, args);
    }

    /**
    * Helpful binary logging for debugging.
    * */
    public void logBytes(byte[] src) {
        this.logBytes(src, 4, false);
    }

    /**
     * Helpful binary logging for debugging.
     * */
    public void logBytes(byte[] src, int bytesPerLine, boolean reverse) {
        lock.lock();
        try {
            for (int offset = 0; offset < src.length; offset += bytesPerLine) {
                int end = Math.min(offset + bytesPerLine, src.length);
                StringBuilder line = new StringBuilder();
                if (reverse) {
                    for (int i = end - 1; i >= offset; i--) {
                        if (!line.isEmpty()) line.append(" ");
                        line.append(stringifyByte(src[i]));
                    }
                } else {
                    for (int i = offset; i < end; i++) {
                        if (!line.isEmpty()) line.append(" ");
                        line.append(stringifyByte(src[i]));
                    }
                }
                verbose(line.toString());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Converts a byte into a series of 1's and 0's.
     * */
    private String stringifyByte(byte src) {
        int unsignedByte = src & 0xFF;
        String binary = Integer.toBinaryString(unsignedByte);
        return String.format("%8s", binary).replace(' ', '0');
    }

    /**
     * Change the logging level mid-run. Helpful for debugging.
     */
    public void setLevel(LogType logType) {
        this.minLevel = logType;
    }

    // Creates a logging session for multi-call log blocks. Helpful for dumping objects.
    public void lock() {
        lock.lock();
    }

    // Finishes a logging session for multi-call log blocks.
    public void unlock() {
        lock.unlock();
    }
}