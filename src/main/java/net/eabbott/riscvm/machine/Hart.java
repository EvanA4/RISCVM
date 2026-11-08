package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.memory.MemoryManager;
import net.eabbott.riscvm.machine.register.CSRFile;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.machine.register.RegisterFile;
import net.eabbott.riscvm.machine.stage.*;
import net.eabbott.riscvm.machine.thread.StageThread;
import net.eabbott.riscvm.util.BinaryUtil;

import java.util.concurrent.CyclicBarrier;

/*
* Hart definition of the virtual machine. Has its own PC, ID, pipeline, and CSR and register files.
* */
public class Hart {
    public Thread[] threads;
    public RegisterFile registers;
    public int programCounter;
    public CSRFile csrFile;
    public final int id;
    public PipelineRegister[] pipelineRegisters;
    public BranchPredictor branchPredictor;
    public Context context;
    public HartStatus status;
    public MemoryManager memman;
    public final AbstractStage[] stages;

    public Hart(int mHartID, Context context) {
        this.context = context;
        this.id = mHartID;
        this.programCounter = context.elf.header.entry;
        this.csrFile = new CSRFile(mHartID, context);
        this.pipelineRegisters = new PipelineRegister[4];
        for (int i = 0; i < 4; ++i) this.pipelineRegisters[i] = new PipelineRegister();
        this.branchPredictor = new BranchPredictor(context.branchPredictionRows, (byte) (context.defaultPrediction & 0b11));
        this.status = HartStatus.RUNNING;
        this.registers = new RegisterFile();
        this.memman = new MemoryManager(this, context);

        // Initialize the stage objects
        this.stages = new AbstractStage[]{
            new FetchStage(this),
            new DecodeStage(this),
            new ExecuteStage(this),
            new MemoryStage(this),
            new WriteStage(this),
        };

        // Initialize and run each pipeline stage thread
        this.threads = new Thread[5];
        for (int i = 0; i < 5; ++i) threads[i] = new StageThread(this.stages[i]);
        for (int i = 0; i < 5; ++i) threads[i].start();
    }

    public boolean hasLoadUseHazard() {
        byte[] decodeInst = pipelineRegisters[0].getInst();
        byte[] executeInst = pipelineRegisters[1].getInst();

        if (decodeInst.length == 0 || executeInst.length == 0) return false;

        int executeOpcodeBits =
                BinaryUtil.intFromBits(executeInst, 0, 7, false);
        if (Opcode.fromInt(executeOpcodeBits) != Opcode.LOAD) return false;

        int rd = BinaryUtil.intFromBits(executeInst, 7, 5, false);
        if (rd == 0) return false;

        int decodeOpcodeBits =
                BinaryUtil.intFromBits(decodeInst, 0, 7, false);
        Opcode decodeOpcode = Opcode.fromInt(decodeOpcodeBits);
        if (decodeOpcode == null) return false;

        int rs1 = BinaryUtil.intFromBits(decodeInst, 15, 5, false);
        boolean usesRs2 = switch (decodeOpcode) {
            case OP, STORE, BRANCH, AMO -> true;
            default -> false;
        };
        int rs2 = usesRs2
                ? BinaryUtil.intFromBits(decodeInst, 20, 5, false)
                : -1;

        return rs1 == rd || rs2 == rd;
    }
}

/*
* Fancy pipeline operations:
* - Branch mispredictions: SQUASH a stage
* - Stale registers between writeback and decode: FORWARD
* - Decode needs a load value: STALL and FORWARD
* */