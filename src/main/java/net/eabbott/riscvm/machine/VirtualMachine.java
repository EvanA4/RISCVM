package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.util.Nullable;

/*
* The core virtual machine class, with multiple-hart capacity.
* */
public class VirtualMachine {
    private static VirtualMachine instance;
    private Hart[] harts;
    private int numParkedHarts = 0;
    private boolean isExiting = false;

    public Context context;
    public VMClock clock;
    public RandomAccessMemory ram;

    public static @Nullable VirtualMachine create(Context context) {
        instance = new VirtualMachine();
        instance.context = context;
        instance.ram = new RandomAccessMemory(context.ramSize);
        if (!instance.ram.populate(context.elf)) {
            IO.println("Failed to populate RAM with ELF data.");
            return null;
        }

        // Initializing the clock does NOT automatically start it
        instance.clock = new VMClock(context.numHarts, context.cycleFrequency);

        // Initializing the harts automatically starts the pipeline stage threads
        instance.harts = new Hart[context.numHarts];
        for (int i = 0; i < context.numHarts; ++i) {
            instance.harts[i] = new Hart(i, context.elf.header.entry);
        }

        return instance;
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