package net.eabbott.riscvm.machine.memory.cache;

/**
 * A record representing the outcome of pretty much all memory operations.
 */
public record MemoryResult(int shouldWait, byte[] data) {}
