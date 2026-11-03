package net.eabbott.riscvm.machine.memory.virtual;

public enum FaultType {
    INSTRUCTION_PAGE_FAULT,
    LOAD_PAGE_FAULT,
    STORE_AMO_PAGE_FAULT,
    ACCESS_FAULT
}