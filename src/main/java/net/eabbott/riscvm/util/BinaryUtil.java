package net.eabbott.riscvm.util;

import java.io.RandomAccessFile;
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
    * Reads "size" bits at "offset" into an integer
    * */
    public static int intFromBytes(byte[] bytes, int offset, int size) {
        int output = 0;
        for (int i = offset + size - 1; i >= offset; --i) {
            output <<= 8;
            output |= bytes[i] & 0xff;
        }
        return output;
    }

    /*
     * Reads "size" bits at "offset" into a long
     * */
    public static long longFromBytes(byte[] bytes, int offset, int size) {
        long output = 0;
        for (int i = offset + size - 1; i >= offset; --i) {
            output <<= 8;
            output |= bytes[i] & 0xff;
        }
        return output;
    }
}