package net.eabbott.riscvm;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

/**
* The driver function of the entire process.
* */
public class Main {
    static void main(String[] args) {
        // Parse arguments, exit if failed
        Context context = Context.parseArgs(args);
        if (context == null) return;

        VMLogger.create(context.logLevel);
        context.dump();

        // If arguments were valid, create and run the virtual machine
        VirtualMachine vm = VirtualMachine.create(context);
        if (vm == null) return;

        vm.start();

        VMLogger.getInstance().fatal("Exiting with code %d", vm.exitCode);
        System.exit(vm.exitCode);
    }
}
