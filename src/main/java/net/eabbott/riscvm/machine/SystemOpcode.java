package net.eabbott.riscvm.machine;

public enum SystemOpcode {
    NONE(-1),
    EXIT(0),
    PUT_CHAR(16),
    GET_CHAR(17),
    START_HART(32),
    STOP_HART(33),
    HART_STATUS(34);

    private final int value;

    SystemOpcode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SystemOpcode fromValue(int value) {
        return switch (value) {
            case 0 -> EXIT;
            case 16 -> PUT_CHAR;
            case 17 -> GET_CHAR;
            case 32 -> START_HART;
            case 33 -> STOP_HART;
            case 34 -> HART_STATUS;
            default -> NONE;
        };
    }
}
