package net.eabbott.riscvm.machine;

import net.eabbott.riscvm.machine.instruction.ALUOp;

public final class ALU {
    private int result;
    private boolean negative;
    private boolean zero;
    private boolean carry;
    private boolean overflow;

    public int execute(ALUOp op, int a, int b) {
        // Assume false for all NZCV
        negative = false;
        zero = false;
        carry = false;
        overflow = false;

        switch (op) {
            case Add -> executeAdd(a, b);
            case Sub -> executeSub(a, b);

            case SLT -> {
                executeSub(a, b);
                result = (negative != overflow) ? 1 : 0;
                updateNegativeAndZero();
            }
            case SLTU -> {
                executeSub(a, b);
                result = carry ? 0 : 1;
                updateNegativeAndZero();
            }

            case Mul -> result = a * b;
            case MulH ->
                    result = (int) (((long) a * (long) b) >> 32);
            case MulHSU ->
                    result = (int) (((long) a * Integer.toUnsignedLong(b)) >> 32);
            case MulHU ->
                    result = (int) ((Integer.toUnsignedLong(a) * Integer.toUnsignedLong(b)) >>> 32);


            case Div -> result = (b == 0)
                    ? -1
                    : (a == Integer.MIN_VALUE && b == -1
                    ? Integer.MIN_VALUE
                    : a / b);
            case DivU -> result = (b == 0)
                    ? -1
                    : Integer.divideUnsigned(a, b);

            case Rem -> result = (b == 0)
                    ? a
                    : (a == Integer.MIN_VALUE && b == -1
                    ? 0
                    : a % b);

            case RemU -> result = (b == 0)
                    ? a
                    : Integer.remainderUnsigned(a, b);

            case LeftShift -> result = a << (b & 0x1f);
            case RightShiftA -> result = a >> (b & 0x1f);
            case RightShiftL -> result = a >>> (b & 0x1f);
            case Or -> result = a | b;
            case Xor -> result = a ^ b;
            case And -> result = a & b;
            case None -> result = 0;
        }

        return result;
    }

    private void executeAdd(int a, int b) {
        long ua = Integer.toUnsignedLong(a);
        long ub = Integer.toUnsignedLong(b);
        long wide = ua + ub;

        result = (int) wide;
        carry = ((wide >>> 32) & 1L) != 0;
        overflow = ((~(a ^ b) & (a ^ result)) < 0);
        updateNegativeAndZero();
    }

    private void executeSub(int a, int b) {
        long ua = Integer.toUnsignedLong(a);
        long ub = Integer.toUnsignedLong(b);
        long notB = (~ub) & 0xffff_ffffL;
        long wide = ua + notB + 1L;

        result = (int) wide;
        // For subtraction, carry=true means no unsigned borrow (a >= b).
        carry = ((wide >>> 32) & 1L) != 0;
        overflow = (((a ^ b) & (a ^ result)) < 0);
        updateNegativeAndZero();
    }

    private void updateNegativeAndZero() {
        negative = (result & 0x8000_0000) != 0;
        zero = result == 0;
    }

    public int getResult() {
        return result;
    }

    public boolean isNegative() {
        return negative;
    }

    public boolean isZero() {
        return zero;
    }

    public boolean isCarry() {
        return carry;
    }

    public boolean isOverflow() {
        return overflow;
    }
}

