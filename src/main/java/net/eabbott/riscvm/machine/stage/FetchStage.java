package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.register.InstructionRegister;
import net.eabbott.riscvm.util.VMLogger;

import java.nio.ByteBuffer;

/*
 * The pipeline fetch stage.
 * */
public class FetchStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final VirtualMachine vm;

    public FetchStage(Hart hart) {
        this.hart = hart;
        this.vm = VirtualMachine.getInstance();
    }

    public void rising() {
        logger.log("Running rising edge for fetch stage for hart #%d", this.hart.id);

        // Determine program counter
        long currentPC = this.hart.programCounter;
        long nextPC = hart.branchPredictor.getBranch(currentPC);
        this.hart.programCounter = nextPC == -1 ? currentPC + 1 : nextPC;

        // Load raw instruction into new instruction register
        byte[] rawInstruction = this.vm.ram.read(currentPC, 4);
        byte[] flipped = ByteBuffer.wrap(rawInstruction).flip().array();
        InstructionRegister instructionRegister = new InstructionRegister();
        instructionRegister.inst = rawInstruction;

        // Write results to pipeline register
        this.hart.pipelineRegisters[0].setProgramCounter(currentPC);
        this.hart.pipelineRegisters[0].setInstructionRegister(instructionRegister);
    }

    public void falling() {
        logger.log("Running falling edge for fetch stage for hart #%d", this.hart.id);
    }
}
