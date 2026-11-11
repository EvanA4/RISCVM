package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
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

    public FetchStage(Hart hart) {
        this.hart = hart;
        this.nextReg = this.hart.pipelineRegisters[0];
    }

    public void rising() {
        this.hartWasParked = this.hart.status != HartStatus.RUNNING;
        if (this.hartWasParked) {
            this.needToNOP = true;
        }

        logger.log("Running rising edge for fetch stage for hart #%d", this.hart.id);

        // Determine program counter
        int currentPC = this.hart.programCounter;
        this.previousCounter = currentPC;
        int nextPC = hart.branchPredictor.getBranch(currentPC);
        this.hart.programCounter = nextPC == -1 ? currentPC + 4 : nextPC;
        int predictedNextPc = nextPC == -1 ? currentPC + 4 : nextPC;
        this.nextReg.setPredictedNextPc(predictedNextPc);

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
        logger.log("Running falling edge for fetch stage for hart #%d", this.hart.id);

        // Update CSR file
        this.hart.csrFile.updateTime();
        if (!this.hartWasParked) this.hart.csrFile.incrementCycle();
        if (!this.needToNOP && !this.needToFreeze) this.hart.csrFile.incrementInstret();

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
