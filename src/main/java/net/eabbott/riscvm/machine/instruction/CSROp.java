package net.eabbott.riscvm.machine.instruction;

/**
 * An enum representing the CSR operation
 */
public enum CSROp {
    CSRRW(0b001),
    CSRRS(0b010),
    CSRRC(0b011),
    CSRRWI(0b101),
    CSRRSI(0b110),
    CSRRCI(0b111);

    final int value;

    CSROp(int value) {
        this.value = value;
    }

    // Parses the CSR operation from a non-zero funct3 field
    public static CSROp fromValue(int value) {
        return switch (value) {
            case 0b001 -> CSRRW;
            case 0b010 -> CSRRS;
            case 0b011 -> CSRRC;
            case 0b101 -> CSRRWI;
            case 0b110 -> CSRRSI;
            case 0b111 -> CSRRCI;
            default -> null;
        };
    }
}
