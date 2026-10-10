package net.eabbott.riscvm.machine.register;

public class InstructionRegister {
    public byte[] inst;
    public int left, right, result;
    public int disp, strval;
    public byte rd;
    public byte memop, aluop;
}
