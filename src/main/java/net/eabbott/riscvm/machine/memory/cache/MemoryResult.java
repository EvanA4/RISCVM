package net.eabbott.riscvm.machine.memory.cache;

public record MemoryResult(boolean shouldWait, byte[] data) {}
