package net.eabbott.riscvm.machine.thread;

import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.stage.AbstractStage;
import net.eabbott.riscvm.util.VMLogger;

import java.util.concurrent.BrokenBarrierException;

/*
* The thread class responsible for executing a stage.
* */
public class StageThread extends Thread {
    VirtualMachine vm = VirtualMachine.getInstance();
    VMLogger logger = VMLogger.getInstance();
    AbstractStage stage;

    public StageThread(AbstractStage stage) {
        this.stage = stage;
    }

    /*
    * Actual function for running a pipeline stage on repeat.
    * */
    @Override
    public void run() {
        // At startup, wait for the clock to signal every pipeline to start
        try {
            vm.clock.await();
        } catch (Exception e) {
            return;
        }

        // Keep waiting on the clock and executing the rising/falling functions
        while (!isInterrupted() && !vm.isExiting()) {
            try {
                stage.rising();
                if (isInterrupted() || vm.isExiting()) break;
                vm.clock.await();
                stage.falling();
                if (isInterrupted() || vm.isExiting()) break;
                vm.clock.await();
            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                logger.log("Detected a broken barrier: %s", e.getMessage());
                break;
            }
        }
    }
}
