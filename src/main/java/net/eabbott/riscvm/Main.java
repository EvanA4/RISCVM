package net.eabbott.riscvm;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.util.BinaryUtil;

/*
* The driver function of the entire process.
* */
public class Main {
    static void main(String[] args) {
        // Parse arguments, exit if failed
        Context context = Context.parseArgs(args);
        if (context == null) {
            System.exit(-1);
            return;
        }

        // If arguments were valid, create and run the virtual machine
        VirtualMachine vm = new VirtualMachine(context);
        vm.start();
    }
}
