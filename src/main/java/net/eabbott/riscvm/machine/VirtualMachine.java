package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;

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
        this.clock = new VMClock(context.numHarts, context.cycleFrequency);
        this.harts = new Hart[context.numHarts];
        for (int i = 0; i < context.numHarts; ++i) {
            this.harts[i] = new Hart(i);
        }
    }


    public static VirtualMachine getInstance() {
        return instance;
    }

    public boolean isExiting() {
        return this.isExiting;
    }

    public void start() {
        this.clock.start();
    }

    public void exit() {
        this.isExiting = true;
        for (int i = 0; i < context.numHarts; ++i) {
            this.harts[i].exit();
        }
    }
}