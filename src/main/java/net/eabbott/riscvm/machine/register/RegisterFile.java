package net.eabbott.riscvm.machine.register;

import net.eabbott.riscvm.util.VMLogger;

/*
* Each virtual machine's hart's register file.
* */
public class RegisterFile {
    // Array of "uninitialized" integers
    int[] registers = new int[32];
    final VMLogger logger = VMLogger.getInstance();

    /*
    * Read a register by its number, with 0 always returning 0
    * */
    public int read(int register) {
        if (register == 0) return 0;
        return this.registers[register];
    }

    /*
    * Write a value to a register by its number
    * */
    public void write(int register, int value) {
        if (register != 0) {
            logger.verbose("Writing %d to register #%d", value, register);
            this.registers[register] = value;
        }
    }
}
