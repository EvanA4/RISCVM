package net.eabbott.riscvm;

public class Main {
    static void main(String[] args) {
        if (args.length == 0) {
            IO.println("No arguments to print, quitting...");
        }

        for (int i = 0; i < args.length; ++i) {
            IO.println(String.format("[%d] %s", i, args[i]));
        }
    }
}
