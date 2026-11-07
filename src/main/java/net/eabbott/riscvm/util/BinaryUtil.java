package net.eabbott.riscvm.util;

public class BinaryUtil {
    /**
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

    /**
     * Reads "size" bits at "offset" into an integer
     * */
    public static int intFromBits(byte[] bytes, int offset, int size, boolean extend) {
        int output = 0;
        for (int i = offset + size - 1; i >= offset; --i) {
            output <<= 1;
            output |= (bytes[i / 8] >> (i % 8)) & 1;
        }
        return extend ? sext(output, size - 1) : output;
    }

    /**
    * Reads "size" bytes at "offset" into an integer in little endian format.
    * */
    public static int intFromBytesLE(byte[] bytes, int offset, int size) {
        int output = 0;
        for (int i = offset + size - 1; i >= offset; --i) {
            output <<= 8;
            output |= bytes[i] & 0xff;
        }
        return output;
    }

    /**
     * Converts integer to byte array in little endian format.
     */
    public static byte[] bytesFromIntLE(int num) {
        byte[] output = new byte[4];
        for (int i = 0; i < 4; ++i) {
            output[i] = (byte) (num >>> (8 * i));
        }
        return output;
    }

    /**
     * Reads "size" bytes at "offset" into a long in little endian format.
     * */
    public static long longFromBytesLE(byte[] bytes, int offset, int size) {
        long output = 0;
        for (int i = offset + size - 1; i >= offset; --i) {
            output <<= 8;
            output |= bytes[i] & 0xff;
        }
        return output;
    }
}