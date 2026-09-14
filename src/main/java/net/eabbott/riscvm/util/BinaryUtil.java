package net.eabbott.riscvm.util;

import java.nio.ByteBuffer;

public class BinaryUtil {
    public static int sext(int src, int idx) {
        // set bits after idx to 0
        // (accounts for when src is positive)
        src &= (1 << (idx + 1)) - 1;

        // create mask with all bits after idx as 1
        int mask = -1;
        mask ^= (1 << idx) - 1;

        // set mask to 0 if src is positive
        mask *= (src >> idx) & 1;

        // put mask on src
        return src | mask;
    }

    public static byte[] bytes(int src) {
        byte[] output = ByteBuffer.allocate(4).putInt(src).array();
        for (int i = 0; i < 2; ++i) {
            byte temp = output[i];
            output[i] = output[3-i];
            output[3-i] = temp;
        }
        return output;
    }
}