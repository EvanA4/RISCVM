package net.eabbott.riscvm.machine.memory;

/**
 * A record representing the outcome of an AMO operation.
 */
public record AMOResult(int shouldWait, int data) {}
