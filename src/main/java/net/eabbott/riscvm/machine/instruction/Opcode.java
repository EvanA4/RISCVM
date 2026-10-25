package net.eabbott.riscvm.machine.instruction;

import net.eabbott.riscvm.util.Nullable;

public enum Opcode {
    LOAD(0b00_000_11),
    STORE(0b01_000_11),
    BRANCH(0b11_000_11),
    JALR(0b11_001_11),
    JAL(0b11_011_11),
    OP_IMM(0b00_100_11),
    OP(0b01_100_11),
    SYSTEM(0b11_100_11),
    AUIPC(0b00_101_11),
    LUI(0b01_101_11),
    AMO(0b01_011_11);

    public final int value;

    Opcode(int value) {
        this.value = value;
    }

    public @Nullable static Opcode fromInt(int value) {
        return switch (value) {
            case 0b00_000_11 -> LOAD;
            case 0b01_000_11 -> STORE;
            case 0b11_000_11 -> BRANCH;
            case 0b11_001_11 -> JALR;
            case 0b11_011_11 -> JAL;
            case 0b00_100_11 -> OP_IMM;
            case 0b01_100_11 -> OP;
            case 0b11_100_11 -> SYSTEM;
            case 0b00_101_11 -> AUIPC;
            case 0b01_101_11 -> LUI;
            case 0b01_011_11 -> AMO;
            default -> null;
        };
    }
}
