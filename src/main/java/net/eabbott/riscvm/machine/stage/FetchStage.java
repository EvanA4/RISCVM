package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.memory.cache.MemoryResult;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.memory.virtual.MemoryFault;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

import java.nio.ByteBuffer;

/**
 * The pipeline fetch stage.
 * */
public class FetchStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister nextReg;
    private int previousCounter = 0;
    boolean hartWasParked = false;
    public int overwritePC = -1;

    private int toWait = 0;
    private byte[] fetchResult = null;

    public FetchStage(Hart hart) {
        this.hart = hart;
        this.nextReg = this.hart.pipelineRegisters[0];
    }

    // Executes the rising edge of the FETCH stage
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
        if (this.hartWasParked || toWait > 0) this.needToNOP = true;

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
        byte[] rawInstruction;
        MemoryResult result = null;
        if (hart.status == HartStatus.RUNNING && toWait == 0) {
            if (fetchResult != null) {
                result = new MemoryResult(0, fetchResult);
                fetchResult = null;
            } else {
                try {
                    result = this.hart.memman.read(currentPC, 4, AccessType.FETCH);
                }  catch (MemoryFault e) {
                    VirtualMachine.getInstance().parkHart(hart.id, HartStatus.fromMemoryFault(e));
                }
            }
        }
        if (toWait > 0) --toWait;

        // If failed to fetch, stall
        if (result == null
                || result.data() == null
                || BinaryUtil.intFromBytesLE(result.data(), 0, 4) == 0) {
            logger.verbose("Failed to fetch instruction!");
            needToNOP = true;
            hart.programCounter = previousCounter;

        } else if (result.shouldWait() > 0) {
            logger.verbose("Failed to fetch instruction, stalling %d cycles!", result.shouldWait());
            toWait = result.shouldWait() - 1;
            fetchResult = result.data();
            needToNOP = true;
            hart.programCounter = previousCounter;

        } else {
            rawInstruction = result.data();
            byte[] flipped = ByteBuffer.wrap(rawInstruction).flip().array();
            this.nextReg.setInst(flipped);
            this.nextReg.setPC(currentPC);
        }
    }

    // Executes the falling edge of the FETCH stage
    public void falling() {
        logger.debug("Running falling edge for fetch stage for hart #%d", this.hart.id);
        logger.debug("Hart status: %s", this.hart.status.name());

        // Update CSR file
        this.hart.csrFile.updateTime();
        if (!this.hartWasParked) this.hart.csrFile.incrementCycle();
        if (!this.needToNOP && !this.needToFreeze) this.hart.csrFile.incrementInstret();

        // If EXECUTE stage overwrites PC, cancel stall
        if (this.overwritePC != -1) {
            this.hart.programCounter = overwritePC;
            toWait = 0;
            fetchResult = null;
        }

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
