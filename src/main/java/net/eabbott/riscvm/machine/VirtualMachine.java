package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.context.Context;
import net.eabbott.riscvm.machine.memory.RAM;
import net.eabbott.riscvm.machine.memory.cache.L3Cache;
import net.eabbott.riscvm.machine.memory.virtual.TLB;
import net.eabbott.riscvm.machine.register.CSRFile;
import net.eabbott.riscvm.util.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/*
* The core virtual machine class, with multiple-hart capacity.
* */
public class VirtualMachine {
    private static VirtualMachine instance;
    public boolean isExiting = false;

    public final ConcurrentNavigableMap<Integer, SystemCall> calls =
        new ConcurrentSkipListMap<>();
    public int[] callResults;

    public Hart[] harts;
    public int numParkedHarts = 0;
    public Context context;
    public VMClock clock;
    public RAM ram;
    public L3Cache l3;
    public TLB tlb;

    public static @Nullable VirtualMachine create(Context context) {
        instance = new VirtualMachine();
        instance.context = context;
        instance.ram = new RAM(context.ramSize);
        instance.l3 = new L3Cache(context.l3Cache, instance.ram);
        instance.tlb = new TLB(context.numTLBSlots, context.tlbEviction);
        instance.callResults = new int[context.numHarts];

        try {
            instance.ram.populate(context.elf);
        } catch (IOException e) {
            IO.println("Failed to populate RAM with ELF data.");
            return null;
        }

        // Initializing the clock does NOT automatically start it
        instance.clock = new VMClock(context.numHarts, context.cycleFrequency);

        // Initializing the harts automatically starts the pipeline stage threads
        instance.harts = new Hart[context.numHarts];
        for (int i = 0; i < context.numHarts; ++i) {
            instance.harts[i] = new Hart(i, context);
        }

        return instance;
    }

    /**
     * Makes a system call.
     */
    public int systemCall(SystemCall call) {
        // Add call to a queue
        calls.put(call.hartId(), call);
        try {
            // Wait for other harts
            clock.callBarrier.await();

            // Wait for main thread to process calls
            clock.callBarrier.await();
        } catch (Exception _) {}

        return callResults[call.hartId()];
    }

    /**
     * Handle the current tick's system calls. Should only be called by main thread.
     */
    public void processCalls() {
        Arrays.fill(callResults, 0);
        for (SystemCall current : calls.values()) {
            boolean shouldBreak = false;
            switch (current.op()) {
                case NONE -> {}
                case EXIT -> {
                    isExiting = true;
                    shouldBreak = true;
                }
                case PUT_CHAR -> context.machineIO.print((char) (current.a0() & 0xFF));
                case GET_CHAR -> callResults[current.hartId()] = context.machineIO.read();
                case START_HART -> {
                    if (
                        current.hartId() < 0 ||
                        current.hartId() >= harts.length ||
                        harts[current.a0()].status == HartStatus.RUNNING
                    ) {
                        callResults[current.hartId()] = 0;
                    } else {
                        callResults[current.hartId()] = 1;
                        unparkHart(current.a0(), current.a1(), current.a2(), current.a3());
                    }
                }
                case STOP_HART -> parkHart(current.hartId(), HartStatus.STOPPED);
                case HART_STATUS -> {
                    if (current.a0() < 0 || current.a0() >= harts.length) {
                        callResults[current.hartId()] = -1;
                    } else {
                        callResults[current.hartId()] = harts[current.a0()].status.ordinal();
                    }
                }
            }
            if (shouldBreak) break;
        }
    }

    /**
     * Parks a hart for a cause.
     */
    public void parkHart(int id, HartStatus cause) {
        this.harts[id].status = cause;
        this.harts[id].memman.invalidateAll();
        for (int i = 0; i < 4; ++i) {
            this.harts[id].stages[i].needToNOP = true;
        }
        if (++numParkedHarts == harts.length) isExiting = true;
    }

    /**
     * Unparks a hart.
     */
    public void unparkHart(int id, int satp, int pc, int a3) {
        this.harts[id].status = HartStatus.RUNNING;
        this.harts[id].csrFile.write(CSRFile.SATP, satp);
        this.harts[id].programCounter = pc;
        this.harts[id].registers.write(13, a3);
        for (int i = 0; i < 4; ++i) {
            // One final NOP/freeze request to clear falling edge
            this.harts[id].stages[i].needToNOP = true;
            this.harts[id].stages[i].needToFreeze = true;
        }
        --numParkedHarts;
    }

    /**
    * A cheeky function allowing for a global VirtualMachine object.
    * */
    public static VirtualMachine getInstance() {
        return instance;
    }

    /**
    * Accessor function for whether the process needs to exit.
    * */
    public boolean isExiting() {
        return this.isExiting;
    }

    /**
    * Actually starts the clock on the main thread.
    * */
    public void start() {
        this.clock.start();
    }
}

/*
* TODO:
* part 5:
*   operand forwarding
* part 6:
*   sign extending loaded memory in MemoryStage if necessary
*   CSR register file, update variables each clock tick on falling edge?
*   CSR instructions
*   write-use stalling (there cannot be a write operation in backend
*       during DECODE which could affect DECODE's register values, stall if necessary)
*
*   AMO operands "rl" and "aq"?? May be satisfied by default.
*   Accessed/dirty bits for pages in page table entries?
* */