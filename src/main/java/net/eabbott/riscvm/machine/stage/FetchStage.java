package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
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
        int nextPC = hart.branchPredictor.getBranch(currentPC);
        this.hart.programCounter = nextPC == -1 ? currentPC + 4 : nextPC;

        // Load raw instruction into new instruction register
        byte[] rawInstruction = this.vm.ram.read(currentPC, 4);
        byte[] flipped = ByteBuffer.wrap(rawInstruction).flip().array();
        this.nextReg.setInst(flipped);
        this.nextReg.setPC(currentPC);
    }

    public void falling() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for fetch stage for hart #%d", this.hart.id);
    }
}
