package net.eabbott.riscvm.machine;

public class RegisterFile {
    public static int STACK_POINTER = 2;
    int[] registers = new int[32];

    public int read(int register) {
        if (register == 0) return 0;
        return this.registers[register];
    }

    public void write(int register, int value) {
        if (register != 0) this.registers[register] = value;
    }
}
