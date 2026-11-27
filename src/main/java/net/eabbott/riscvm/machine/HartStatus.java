package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.machine.memory.virtual.MemoryFault;

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
    ACCESS_FAULT;

    public static HartStatus fromMemoryFault(MemoryFault fault) {
        return switch (fault.type) {
            case INSTRUCTION_PAGE_FAULT -> FETCH_FAULT;
            case LOAD_PAGE_FAULT -> LOAD_FAULT;
            case STORE_AMO_PAGE_FAULT -> AMO_STORE_FAULT;
            case ACCESS_FAULT -> ACCESS_FAULT;
        };
    }
}
