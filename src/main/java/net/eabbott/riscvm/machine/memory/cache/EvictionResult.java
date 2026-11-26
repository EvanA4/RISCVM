package net.eabbott.riscvm.machine.memory.cache;

/**
 * A record representing the outcome of an eviction attempt.
 */
public record EvictionResult(long block, byte[] data, boolean wasModified) {}