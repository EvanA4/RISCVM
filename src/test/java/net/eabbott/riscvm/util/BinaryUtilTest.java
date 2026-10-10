package net.eabbott.riscvm.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

class BinaryUtilTest {

    @Test
    void sext() {
        VMLogger logger = new VMLogger();

        int example = 0b110101;
        for (int i = 0; i < 32; ++i) {
            int extended = BinaryUtil.sext(example, i);
            byte[] nyteArray = ByteBuffer.allocate(4).putInt(extended).array();;
            logger.logBytes(nyteArray, 4, false);
        }
    }
}