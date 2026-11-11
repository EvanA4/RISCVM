package net.eabbott.riscvm.machine.instruction;

public enum ALUOp {
    Add,         // Signed addition
    Sub,         // Signed subtraction (or comparison for BRANCH)

    // M extension
    Mul,         // Signed multiplication (lower bits)
    MulH,        // Signed multiplication (higher bits)
    MulHSU,      // Signed-unsigned multiplication (higher bits)
    MulHU,       // Unsigned multiplication (higher bits)
    Div,         // Signed division
    DivU,        // Unsigned division
    Rem,         // Signed remainder
    RemU,        // Unsigned remainder (modulo)

    LeftShift,   // Logical left shift
    RightShiftA, // Arithmetic right shift
    RightShiftL, // Logical right shift
    Or,          // Bitwise inclusive OR
    Xor,         // Bitwise exclusive OR
    SLT,         // Set less than
    SLTU,        // Unsigned set less than
    And,         // Bitwise AND
    None;

    public byte toByte() {
        return (byte) this.ordinal();
    }

    public static ALUOp fromByte(byte src) {
        return ALUOp.values()[src];
    }

    public static boolean isM(ALUOp aluOp) {
        return switch (aluOp) {
            case Mul, MulH, MulHSU, MulHU, Div, DivU, Rem, RemU -> true;
            default -> false;
        };
    }
}
