package net.eabbott.riscvm.machine.instruction;

/**
 * An enum representing the ALU operation necessary for a given instruction.
 */
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

    // Converts an ALU operation into its corresponding byte
    public byte toByte() {
        return (byte) this.ordinal();
    }

    // Converts a byte into its corresponding ALU operation
    public static ALUOp fromByte(byte src) {
        return ALUOp.values()[src];
    }

    // Specifies whether an ALU operation is a part of the multiplication extension for RISCV
    public static boolean isM(ALUOp aluOp) {
        return switch (aluOp) {
            case Mul, MulH, MulHSU, MulHU, Div, DivU, Rem, RemU -> true;
            default -> false;
        };
    }
}
