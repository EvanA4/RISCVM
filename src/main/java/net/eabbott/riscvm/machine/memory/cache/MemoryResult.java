package net.eabbott.riscvm.machine.memory.cache;

public record MemoryResult(int shouldWait, byte[] data) {}
