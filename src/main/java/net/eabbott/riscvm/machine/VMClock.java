package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.LogType;
import net.eabbott.riscvm.util.VMLogger;

import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/*
* The clock of the virtual machine. Runs on the main thread.
* */
public class VMClock {
    private final VirtualMachine vm = VirtualMachine.getInstance();
    private final VMLogger logger = VMLogger.getInstance();
    private final CyclicBarrier barrier;
    private final Duration halfPeriod;
    private int counter = 0;
    private ClockPhase phase = ClockPhase.RISING;

    public final CyclicBarrier callBarrier;

    public VMClock(int numHarts, long frequency) {
        this.barrier = new CyclicBarrier(numHarts * 5 + 1,
            () -> {
                // This runs atomically between each clock tick
                // Update temporary counter variable
                // if (counter == 245760) {
                //     VMLogger.getInstance().setLevel(LogType.VERBOSE);
                // }
                // if (counter++ == 5000) {
                //     vm.isExiting = true;
                // }

                boolean allHartsParked = true;
                for (Hart hart : vm.harts) {
                    if (hart.status == HartStatus.RUNNING) allHartsParked = false;
                }
                if (allHartsParked) vm.isExiting = true;

                logger.debug("############################ NEW CLOCK TICK ############################");
            }
        );
        this.callBarrier = new CyclicBarrier(numHarts + 1);
        double periodNano = 500_000_000. / (double) frequency;
        this.halfPeriod = Duration.ofNanos(Double.valueOf(periodNano).longValue());
    }

    /*
    * The loop of the clock. Waits for pipeline threads before continuing.
    * */
    public void start() {
        // Keep ticking unless exit requested
        while (!vm.isExiting) {
            try {
                // Sleep for edge
                Thread.sleep(halfPeriod);

                // Update clock phase just before waiting
                switch (this.phase) {
                    case RISING -> {
                        // Clear previous tick's list of system calls
                        vm.calls.clear();

                        this.phase = ClockPhase.FALLING;
                    }
                    case FALLING -> {
                        // Handle any system calls on main thread
                        // Wait for hart threads to make system calls
                        callBarrier.await();

                        // Process tick's list of system calls
                        vm.processCalls();

                        // Notify harts' threads of completion
                        callBarrier.await();

                        this.phase = ClockPhase.RISING;
                    }
                }

                if (vm.isExiting) break;
                barrier.await();

            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                logger.debug("Detected a broken barrier: %s", e.getMessage());
                break;
            }
        }

        logger.debug("Broke out of clock loop. Requesting for threads to die.");
        for (Hart hart : vm.harts) {
            for (Thread thread : hart.threads) {
                thread.interrupt();
            }
        }
    }

    /*
    * Accessor function for pipeline threads who need to wait on the barrier. Should never be called by the clock.
    * */
    public void await() throws BrokenBarrierException, InterruptedException {
        this.barrier.await();
    }
}
