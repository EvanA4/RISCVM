package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.memory.virtual.MemoryFault;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.VMLogger;

import java.nio.ByteBuffer;

/*
 * The pipeline fetch stage.
 * */
public class FetchStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister nextReg;
    private int previousCounter = 0;
    boolean hartWasParked = false;
    public int overwritePC = -1;

    public FetchStage(Hart hart) {
        this.hart = hart;
        this.nextReg = this.hart.pipelineRegisters[0];
    }

    public void rising() {
        // TODO: REMOVE THIS BLOCK WHICH NOPS IF INSTRUCTION IN PIPELINE
        // for (int i = 0; i < 4; ++i) {
        //     Instruction instruction = new Instruction(this.hart.pipelineRegisters[i].getInst());
        //     if (!instruction.isNOP()) {
        //         logger.debug("Instruction already in pipeline! Skipping...");
        //         this.needToNOP = true;
        //         return;
        //     }
        // }
        // logger.debug("No instructions in pipeline! Continuing...");

        this.hartWasParked = this.hart.status != HartStatus.RUNNING;
        if (this.hartWasParked) {
            this.needToNOP = true;
        }

        logger.debug("Running rising edge for fetch stage for hart #%d", this.hart.id);

        // Determine program counter
        int currentPC = this.hart.programCounter;
        this.previousCounter = currentPC;
        int nextPC = hart.branchPredictor.getBranch(currentPC);
        this.hart.programCounter = nextPC == -1 ? currentPC + 4 : nextPC;
        int predictedNextPc = nextPC == -1 ? currentPC + 4 : nextPC;
        this.nextReg.setPredictedNextPc(predictedNextPc);
        logger.debug(
            "Current PC: %d %x    Predicted next PC: %d %x",
            currentPC, currentPC, predictedNextPc, predictedNextPc
        );

        // Load raw instruction into new instruction register
        byte[] rawInstruction = new byte[]{0x13, 0x00, 0x00, 0x00};
        if (hart.status == HartStatus.RUNNING) {
            try {
                rawInstruction = this.hart.memman.fetch(currentPC);
            } catch (MemoryFault e) {
                VirtualMachine.getInstance().parkHart(hart.id, HartStatus.FETCH_FAULT);
            }
        }
        byte[] flipped = ByteBuffer.wrap(rawInstruction).flip().array();
        this.nextReg.setInst(flipped);
        this.nextReg.setPC(currentPC);
    }

    public void falling() {
        logger.debug("Running falling edge for fetch stage for hart #%d", this.hart.id);
        logger.debug("Hart status: %s", this.hart.status.name());

        // Update CSR file
        this.hart.csrFile.updateTime();
        if (!this.hartWasParked) this.hart.csrFile.incrementCycle();
        if (!this.needToNOP && !this.needToFreeze) this.hart.csrFile.incrementInstret();

        if (this.overwritePC != -1) this.hart.programCounter = overwritePC;

        if (this.needToNOP) {
            this.hart.pipelineRegisters[0].setNextToNop();
            this.needToNOP = false;
        } else {
            this.hart.csrFile.incrementInstret();
        }

        if (this.needToFreeze) {
            this.hart.programCounter = previousCounter;
            this.needToFreeze = false;
        } else {
            this.hart.pipelineRegisters[0].tick();
        }
    }
}
