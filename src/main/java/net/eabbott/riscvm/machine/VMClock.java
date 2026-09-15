package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.util.VMLogger;

import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class VMClock {
    private final VirtualMachine vm = VirtualMachine.getInstance();
    private final VMLogger logger = VMLogger.getInstance();
    private final CyclicBarrier barrier;
    private final Duration halfPeriod;
    private int counter = 0;

    public VMClock(int numHarts, long frequency) {
        this.barrier = new CyclicBarrier(numHarts * 5 + 1);
        double periodNano = 500_000_000. / (double) frequency;
        this.halfPeriod = Duration.ofNanos(Double.valueOf(periodNano).longValue());
    }

    public void start() {
        while (!vm.isExiting()) {
            try {
                if (counter == 6) {
                    this.vm.exit();
                    break;
                }
                ++counter;

                Thread.sleep(halfPeriod);
                if (vm.isExiting()) break;
                this.logger.log("New clock tick");
                barrier.await();

            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                logger.log("Detected a broken barrier: %s", e.getMessage());
                break;
            }
        }
    }

    public void await() throws BrokenBarrierException, InterruptedException {
        this.barrier.await();
    }
}
