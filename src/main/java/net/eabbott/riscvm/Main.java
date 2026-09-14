package net.eabbott.riscvm;

import net.eabbott.riscvm.context.Context;

public class Main {
    static void main(String[] args) {
        Context context = Context.parseArgs(args);
        if (context == null) System.exit(-1);
        context.dump();
    }
}
