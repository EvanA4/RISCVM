package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.machine.stage.*;
import net.eabbott.riscvm.machine.thread.StageThread;

public class Hart {
    public Thread[] threads;
    public RegisterFile registers;
    public long programCounter;
    public CSRFile csrFile;
    public final int id;

    public Hart(int mHartID) {
        this.id = mHartID;
        this.csrFile = new CSRFile(mHartID);
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
