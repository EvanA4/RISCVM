package net.eabbott.riscvm.context;

import java.io.FileWriter;

public class VMLogger {
    String fileName;
    FileWriter writer;

    public void open(String fileName) {

    }
    public int log(String text) {
        return 0;
    }

    void logBytes(byte[] src, int bytesPerLine, boolean reverse) {

    }

    void logBytes(byte[] src) {
        this.logBytes(src, 4, true);
    }
}