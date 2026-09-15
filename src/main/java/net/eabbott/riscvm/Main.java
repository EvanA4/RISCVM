package net.eabbott.riscvm;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.util.BinaryUtil;

public class Main {
    static void main(String[] args) {
        Context context = Context.parseArgs(args);
        if (context == null) {
            System.exit(-1);
            return;
        }

        VirtualMachine vm = new VirtualMachine(context);
        vm.start();
    }
}
