package net.eabbott.riscvm.machine.memory.cache;

/**
 * An enum representing the current MESI stage of a MESI cache line.
 */
public enum MESI {
    INVALID, SHARED, EXCLUSIVE, MODIFIED
}
