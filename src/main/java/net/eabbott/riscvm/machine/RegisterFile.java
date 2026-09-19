package net.eabbott.riscvm.machine;

/*
* Each virtual machine's hart's register file.
* */
public class RegisterFile {
    // Array of "uninitialized" integers
    int[] registers = new int[32];

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
        if (register != 0) this.registers[register] = value;
    }
}
