package net.eabbott.riscvm.machine.instruction;

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

    public byte toByte() {
        return (byte) this.ordinal();
    }

    public static AMOOp fromByte(byte src) {
        return AMOOp.values()[src];
    }
}
