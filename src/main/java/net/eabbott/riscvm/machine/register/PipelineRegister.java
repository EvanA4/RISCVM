package net.eabbott.riscvm.machine.register;

/*
* The class representation of a pipeline register. Each pipeline has four of these.
* A special "tick" function rotates the write-only half of the buffer to the read-only half.
* */
public class PipelineRegister {
    private final long[] programCounters;
    private final InstructionRegister[] instructionRegisters;

    public PipelineRegister() {
        this.programCounters = new long[2];
        this.instructionRegisters = new InstructionRegister[2];
    }

    /*
    * Rotates the write-only half of the buffer to the read-only half.
    * */
    public void tick() {
        this.programCounters[1] = this.programCounters[0];
        this.instructionRegisters[1] = this.instructionRegisters[0];
    }

    /*
    * Getters and setters for the pipeline register.
    * Getters read from the read-only half, while the
    * setters write to the write-only half.
    * */

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
