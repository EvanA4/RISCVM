package net.eabbott.riscvm;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.util.BinaryUtil;

public class Main {
    static void main(String[] args) {
        Context context = Context.parseArgs(args);
        if (context == null) {
            System.exit(-1);
            return;
        }

        context.dump();
        for (int i = 0; i < 32; ++i) {
            int extended = BinaryUtil.sext(0b11010111, i);
            context.logger.logBytes(BinaryUtil.bytes(extended), 4, true);
        }
    }
}
