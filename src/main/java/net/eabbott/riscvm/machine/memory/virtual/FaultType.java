package net.eabbott.riscvm.machine.memory.virtual;

/**
 * An enum representing the different kinds of faults which can occur.
 */
public enum FaultType {
    INSTRUCTION_PAGE_FAULT,
    LOAD_PAGE_FAULT,
    STORE_AMO_PAGE_FAULT,
    ACCESS_FAULT
}