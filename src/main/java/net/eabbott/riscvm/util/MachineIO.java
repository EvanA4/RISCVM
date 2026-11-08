package net.eabbott.riscvm.util;

import java.io.FileWriter;
import java.io.IOException;

/*
* The logger for the entire virtual machine application.
* */
public class MachineIO {
    private static MachineIO instance;
    private @Nullable FileWriter writer = null;

    public MachineIO() {
        instance = this;
    }

    /*
    * A cheeky function allowing for a global VMLogger object.
    * */
    public static MachineIO getInstance() {
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
    public void print(char src) {
        if (this.writer != null) {
            try {
                this.writer.write(src);
            } catch (IOException _) {}
        } else {
            IO.print(src);
        }
    }

    /*
     * A basic string formatting log function. Logs to the correct location.
     * */
    public char read() {
        try {
            return (char) System.in.read();
        } catch (IOException e) {
            return (char) -1;
        }
    }
}