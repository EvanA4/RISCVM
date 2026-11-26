package net.eabbott.riscvm.machine.instruction;

/**
 * An enum representing the atomic instruction operation.
 */
public enum AMOOp {
    LR,
    SC,
    SWAP,
    ADD,
    XOR,
    AND,
    OR,
    MIN,
    MAX,
    MINU,
    MAXU,
    INVALID;

    // Converts an AMO operation into its corresponding byte
    public byte toByte() {
        return (byte) this.ordinal();
    }

    // Converts a byte into its corresponding AMO operation
    public static AMOOp fromByte(byte src) {
        return AMOOp.values()[src];
    }
}
