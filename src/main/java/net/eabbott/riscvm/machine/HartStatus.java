package net.eabbott.riscvm.machine;

/**
 * An enum representing the current status of a hart.
 */
public enum HartStatus {
    RUNNING,
    STOPPED,
    AMO_STORE_FAULT,
    LOAD_FAULT,
    FETCH_FAULT,
    ILLEGAL_INSTRUCTION,
    ACCESS_FAULT
}
