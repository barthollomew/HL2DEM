package hl2dem.io;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class VarintReader {

    // Return value packs two values into one long to avoid allocation on the hot path:
    //   bits 63-32 = number of bytes consumed (int)
    //   bits 31-0  = decoded unsigned varint value (int)
    public static long readVarint(MemorySegment seg, long offset) {
        int value = 0;
        int shift = 0;
        int bytesConsumed = 0;
        long limit = seg.byteSize();

        if (offset < 0) {
            throw new IllegalStateException("Varint read at negative offset " + offset);
        }
        while (shift < 35) {
            if (offset + bytesConsumed >= limit) {
                throw new IllegalStateException("Varint extends past end of segment at offset " + offset);
            }
            int b = seg.get(ValueLayout.JAVA_BYTE, offset + bytesConsumed) & 0xFF;
            bytesConsumed++;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return ((long) bytesConsumed << 32) | (value & 0xFFFFFFFFL);
            }
            shift += 7;
        }
        throw new IllegalStateException("Varint too large (>5 bytes) at offset " + offset);
    }

    public static int value(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int bytesConsumed(long packed) {
        return (int) (packed >>> 32);
    }
}
