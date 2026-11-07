package net.eabbott.riscvm.machine.memory;

public record AMOResult(boolean shouldWait, int data) {}
