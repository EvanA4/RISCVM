package net.eabbott.riscvm.machine.thread;

import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.stage.AbstractStage;
import net.eabbott.riscvm.util.VMLogger;

import java.util.concurrent.BrokenBarrierException;

public class StageThread extends Thread {
    VirtualMachine vm = VirtualMachine.getInstance();
    VMLogger logger = VMLogger.getInstance();
    AbstractStage stage;

    public StageThread(AbstractStage stage) {
        this.stage = stage;
    }
    
    @Override
    public void run() {
        try {
            vm.clock.await();
        } catch (Exception e) {
            return;
        }

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
