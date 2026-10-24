package net.eabbott.riscvm.machine.instruction;

public enum ALUOp {
    Add,         // Signed addition
    Sub,         // Signed subtraction (or comparison for BRANCH)
    Mul,         // Signed multiplication
    Div,         // Signed division
    DivU,        // Unsigned division
    Rem,         // Signed remainder
    RemU,        // Unsigned remainder (modulo)
    LeftShift,   // Logical left shift
    RightShiftA, // Arithmetic right shift
    RightShiftL, // Logical right shift
    Or,          // Bitwise inclusive OR
    Xor,         // Bitwise exclusive OR
    And,
    None;         // Bitwise AND

    public byte toByte() {
        return (byte) this.ordinal();
    }

    public static ALUOp fromByte(byte src) {
        return ALUOp.values()[src];
    }
}
