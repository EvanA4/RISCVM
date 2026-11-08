package net.eabbott.riscvm.machine;

public record SystemCall(int hartId, SystemOpcode op, int a0, int a1, int a2, int a3) {
}
