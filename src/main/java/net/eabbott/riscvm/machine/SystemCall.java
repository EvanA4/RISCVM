package net.eabbott.riscvm.machine;

/**
 * A record representing a system call request (or lack thereof if SystemOpcode is NONE)
 */
public record SystemCall(int hartId, SystemOpcode op, int a0, int a1, int a2, int a3) {
}
