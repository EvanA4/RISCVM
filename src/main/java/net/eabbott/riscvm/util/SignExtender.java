package net.eabbott.riscvm.util;

public class SignExtender {
    public static int getInt(int src, int index) {
        return 0;
    }
}

/*
int32_t signext(int32_t src, int32_t idx) {
    // set bits after idx to 0
    // (accounts for when src is positive)
    src &= (1 << (idx + 1)) - 1;

    // create mask with all bits after idx as 1
    int32_t mask = -1;
    mask ^= (1 << idx) - 1;

    // set mask to 0 if src is positive
    mask *= (src >> idx) & 1;

    // put mask on src
    return src | mask;
}
*/