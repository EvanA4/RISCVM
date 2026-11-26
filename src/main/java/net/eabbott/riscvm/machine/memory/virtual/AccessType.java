package net.eabbott.riscvm.machine.memory.virtual;

/**
 * An enum representing the cause for the memory access. This is compared with
 * the permissions of the corresponding page table entry to potentially throw
 * a MemoryFault.
 */
public enum AccessType {
    FETCH,
    LOAD,
    STORE,
    AMO
}