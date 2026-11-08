package net.eabbott.riscvm.machine;

public enum HartStatus {
    RUNNING,
    STOPPED,
    AMO_STORE_FAULT,
    LOAD_FAULT,
    FETCH_FAULT,
    ILLEGAL_INSTRUCTION,
    ACCESS_FAULT
}
