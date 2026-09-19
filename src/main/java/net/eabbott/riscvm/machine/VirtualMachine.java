package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;

/*
* The core virtual machine class, with multiple-hart capacity.
* */
public class VirtualMachine {
    private static VirtualMachine instance;
    private final Hart[] harts;
    private int numParkedHarts = 0;
    private boolean isExiting = false;

    public final Context context;
    public final VMClock clock;
    public RandomAccessMemory ram;

    public VirtualMachine(Context context) {
        instance = this;
        this.context = context;
        this.ram = new RandomAccessMemory(context.ramSize);

        // Initializing the clock does NOT automatically start it
        this.clock = new VMClock(context.numHarts, context.cycleFrequency);

        // Initializing the harts automatically starts the pipeline stage threads
        this.harts = new Hart[context.numHarts];
        for (int i = 0; i < context.numHarts; ++i) {
            this.harts[i] = new Hart(i);
        }
    }

    /*
    * A cheeky function allowing for a global VirtualMachine object.
    * */
    public static VirtualMachine getInstance() {
        return instance;
    }

    /*
    * Accessor function for whether the process needs to exit.
    * */
    public boolean isExiting() {
        return this.isExiting;
    }

    /*
    * Actually starts the clock on the main thread.
    * */
    public void start() {
        this.clock.start();
    }

    /*
    * Terminates the harts' pipeline threads.
    * */
    public void exit() {
        this.isExiting = true;
        for (int i = 0; i < context.numHarts; ++i) {
            this.harts[i].exit();
        }
    }
}