package net.eabbott.riscvm.machine.memory.cache;

public record EvictionResult(long block, byte[] data, boolean wasModified) {}