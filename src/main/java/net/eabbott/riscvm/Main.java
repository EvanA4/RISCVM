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
        if (context == null) return;
        context.dump();

        // If arguments were valid, create and run the virtual machine
        VirtualMachine vm = VirtualMachine.create(context);
        if (vm == null) return;

        vm.start();
    }
}

/*
* TODO:
*  - implement a unit testing schema
*  - create pipeline register array for each hart
*       - should have temporary values (PC,IR,A,B,O,D)
*       - IR should be its own class
 *  - update pipeline register buffer with clock in secret third stage w/o sleep
*       - should happen after every falling-edge tick
*       - requires clock and each thread to do an extra wait on the barrier
*  - instruction fetch stage
*       - simply load 4 bytes from memory at PC into IR for inst field
*  - branch prediction table
*       - map PC address to 2-bit shift register
*       - if PC not in table, return default branch prediction
* */