package net.eabbott.riscvm.util;

import java.nio.ByteBuffer;

public class BinaryUtil {
    /*
    * Sign extends an integer at a given index. We're all adults here, right?
    * */
    public static int sext(int src, int idx) {
        // Set bits after idx to 0
        // (accounts for when src is positive)
        src &= (1 << (idx + 1)) - 1;

        // Create mask with all bits after idx as 1
        int mask = -1;
        mask ^= (1 << idx) - 1;

        // Set mask to 0 if src is positive
        mask *= (src >> idx) & 1;

        // Put mask on src
        return src | mask;
    }

    /*
    * Converts an integer into a byte array
    * */
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