package net.eabbott.riscvm.machine.instruction;

import net.eabbott.riscvm.util.Nullable;

public enum Opcode {
    LOAD(0b00_000),
    STORE(0b01_000),
    BRANCH(0b11_000),
    JALR(0b11_001),
    JAL(0b11_011),
    OP_IMM(0b00_100),
    OP(0b01_100),
    SYSTEM(0b11_100),
    AUIPC(0b00_101),
    LUI(0b01_101),
    AMO(0b11_011),
    ECALL(0b11_10_011);

    public final int value;

    Opcode(int value) {
        this.value = value;
    }

    public @Nullable static Opcode fromInt(int value) {
        return switch (value) {
            case 0b00_000 -> LOAD;
            case 0b01_000 -> STORE;
            case 0b11_000 -> BRANCH;
            case 0b11_001 -> JALR;
            case 0b11_011 -> JAL;
            case 0b00_100 -> OP_IMM;
            case 0b01_100 -> OP;
            case 0b11_100 -> SYSTEM;
            case 0b00_101 -> AUIPC;
            case 0b01_101 -> LUI;
            case 0b01_011 -> AMO;
            case 0b11_10_011 -> ECALL;
            default -> null;
        };
    }
}
