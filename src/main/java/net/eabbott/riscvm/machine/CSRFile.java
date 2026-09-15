package net.eabbott.riscvm.machine;

public class CSRFile {
    public static final int M_HART_ID = 0xF14;
    public static final int SATP = 0x180;
    public static final int CYCLE = 0xC00;
    public static final int CYCLE_H = 0xC80;
    public static final int TIME = 0xC01;
    public static final int TIME_H = 0xC81;
    public static final int INSTRET = 0xC02;
    public static final int INSTRET_H = 0xC82;
    private final int mHartID;
    private int satp;
    private int time;
    private int timeH;
    private int cycle;
    private int cycleH;
    private int instret;
    private int instretH;

    public CSRFile(int mHartID) {
        this.mHartID = mHartID;

        VirtualMachine vm = VirtualMachine.getInstance();
        if (!vm.context.allowMMU) {
            this.satp = 0;
        }
    }

    public int read(int csr) {
        switch (csr) {
            case M_HART_ID -> { return this.mHartID; }
            case SATP -> { return this.satp; }
            case CYCLE -> { return this.time; }
            case CYCLE_H -> { return this.timeH; }
            case TIME -> { return this.cycle; }
            case TIME_H -> { return this.cycleH; }
            case INSTRET -> { return this.instret; }
            case INSTRET_H -> { return this.instretH; }
            default -> throw new IllegalArgumentException(
                String.format("Invalid CSR number: %x", csr)
            );
        }
    }

    public void write(int csr, int value) {
        if (csr == SATP) {
            this.satp = value;
        } else {
            throw new IllegalArgumentException(
                String.format("Invalid CSR number for write: %x", csr)
            );
        }
    }

    public void tick() {
        // update values for read-only values here
    }
}
