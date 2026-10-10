package net.eabbott.riscvm.machine.register;

public class PipelineRegister {
    private final long[] programCounters;
    private final InstructionRegister[] instructionRegisters;

    public PipelineRegister() {
        this.programCounters = new long[2];
        this.instructionRegisters = new InstructionRegister[2];
    }

    public void tick() {
        this.programCounters[1] = this.programCounters[0];
        this.instructionRegisters[1] = this.instructionRegisters[0];
    }

    public long getProgramCounter() {
        return programCounters[1];
    }

    public void setProgramCounter(long programCounter) {
        this.programCounters[0] = programCounter;
    }

    public InstructionRegister getInstructionRegister() {
        return instructionRegisters[1];
    }

    public void setInstructionRegister(InstructionRegister instructionRegister) {
        this.instructionRegisters[0] = instructionRegister;
    }
}
