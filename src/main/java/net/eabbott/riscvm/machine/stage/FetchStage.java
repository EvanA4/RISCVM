package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.VMLogger;

import java.nio.ByteBuffer;

/*
 * The pipeline fetch stage.
 * */
public class FetchStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final VirtualMachine vm;
    private final PipelineRegister nextReg;
    private int previousCounter = 0;

    public FetchStage(Hart hart) {
        this.hart = hart;
        this.vm = VirtualMachine.getInstance();
        this.nextReg = this.hart.pipelineRegisters[0];
    }

    public void rising() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running rising edge for fetch stage for hart #%d", this.hart.id);

        // Determine program counter
        int currentPC = this.hart.programCounter;
        this.previousCounter = currentPC;
        int nextPC = hart.branchPredictor.getBranch(currentPC);
        this.hart.programCounter = nextPC == -1 ? currentPC + 4 : nextPC;
        int predictedNextPc = nextPC == -1 ? currentPC + 4 : nextPC;
        this.nextReg.setPredictedNextPc(predictedNextPc);

        // Load raw instruction into new instruction register
        byte[] rawInstruction = this.hart.memman.read(currentPC, 4, AccessType.FETCH);
        byte[] flipped = ByteBuffer.wrap(rawInstruction).flip().array();
        this.nextReg.setInst(flipped);
        this.nextReg.setPC(currentPC);
    }

    public void falling() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for fetch stage for hart #%d", this.hart.id);
        if (this.needToNOP) {
            this.hart.pipelineRegisters[0].setNextToNop();
            this.needToNOP = false;
        }

        if (this.needToFreeze) {
            this.hart.programCounter = previousCounter;
            this.needToFreeze = false;
        } else {
            this.hart.pipelineRegisters[0].tick();
        }
    }
}
