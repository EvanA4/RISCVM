package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.machine.register.RegisterFile;
import net.eabbott.riscvm.machine.stage.*;
import net.eabbott.riscvm.machine.thread.StageThread;

/*
* Hart definition of the virtual machine. Has its own PC, ID, pipeline, and CSR and register files.
* */
public class Hart {
    public Thread[] threads;
    public RegisterFile registers;
    public long programCounter;
    public CSRFile csrFile;
    public final int id;
    public PipelineRegister[] pipelineRegisters;
    public BranchPredictor branchPredictor;
    public Context context;

    public Hart(int mHartID, Context context) {
        this.context = context;
        this.id = mHartID;
        this.programCounter = context.elf.header.entry;
        this.csrFile = new CSRFile(mHartID);
        this.pipelineRegisters = new PipelineRegister[4];
        this.branchPredictor = new BranchPredictor(context.branchPredictionRows, (byte) (context.defaultPrediction & 0b11));

        // Initialize and run each pipeline stage thread
        this.threads = new Thread[5];
        threads[0] = new StageThread(new FetchStage(this));
        threads[1] = new StageThread(new DecodeStage(this));
        threads[2] = new StageThread(new ExecuteStage(this));
        threads[3] = new StageThread(new MemoryStage(this));
        threads[4] = new StageThread(new WriteStage(this));
        for (int i = 0; i < 5; ++i) threads[i].start();
    }

    public void exit() {
        for (int i = 0; i < 5; ++i) threads[i].interrupt();
    }
}
