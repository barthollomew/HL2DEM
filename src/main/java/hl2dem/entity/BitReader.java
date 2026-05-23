package hl2dem.entity;

// Bit-level reader over a byte array. CS2 entity data is packed in LSB-first order.
final class BitReader {

    private final byte[] buf;
    private final int bitLimit;
    private int bitPos;

    BitReader(byte[] buf, int byteLen) {
        this.buf = buf;
        this.bitLimit = byteLen * 8;
        this.bitPos = 0;
    }

    boolean hasMore() {
        return bitPos < bitLimit;
    }

    int bitsRemaining() {
        return bitLimit - bitPos;
    }

    // Read n bits (0-32), LSB-first.
    int readBits(int n) {
        if (n == 0) return 0;
        int result = 0;
        int bitsRead = 0;
        while (bitsRead < n) {
            int byteIdx = bitPos >> 3;
            int bitIdx = bitPos & 7;
            int chunk = Math.min(n - bitsRead, 8 - bitIdx);
            int bits = (buf[byteIdx] & 0xFF) >>> bitIdx;
            bits &= (1 << chunk) - 1;
            result |= bits << bitsRead;
            bitsRead += chunk;
            bitPos += chunk;
        }
        return result;
    }

    boolean readBool() {
        return readBits(1) != 0;
    }

    // CS2 "UBitVar": 2 bits of header, then 4/8/12/32 bits of value.
    int readUBitVar() {
        int kind = readBits(2);
        return switch (kind) {
            case 0 -> readBits(4);
            case 1 -> readBits(8);
            case 2 -> readBits(12);
            case 3 -> readBits(32);
            default -> throw new IllegalStateException();
        };
    }

    // CS2 "UBitVarFP": used specifically for field path delta encoding.
    int readUBitVarFP() {
        if (readBool()) return readBits(2);
        if (readBool()) return readBits(4);
        if (readBool()) return readBits(10);
        if (readBool()) return readBits(17);
        return readBits(31);
    }

    // Variable-length encoded unsigned 32-bit int (similar to protobuf varint but from bits).
    int readVarUInt32() {
        int result = 0;
        int shift = 0;
        while (shift < 35) {
            int group = readBits(8);
            result |= (group & 0x7F) << shift;
            if ((group & 0x80) == 0) return result;
            shift += 7;
        }
        return result;
    }

    // Signed variable-length int using zigzag encoding.
    int readVarInt32() {
        int n = readVarUInt32();
        return (n >>> 1) ^ -(n & 1);
    }

    // Reads a 32-bit float verbatim.
    float readFloat32() {
        return Float.intBitsToFloat(readBits(32));
    }

    // Reads a quantized float with given bit count and range [low, high].
    float readQuantizedFloat(int bitCount, float low, float high) {
        if (bitCount == 0) return low;
        int maxVal = (1 << bitCount) - 1;
        int rawVal = readBits(bitCount);
        return low + (high - low) * (rawVal / (float) maxVal);
    }

    // Reads an angle in 360-degree space encoded in `bits` bits.
    float readAngle(int bits) {
        int mask = (1 << bits) - 1;
        return readBits(bits) * (360.0f / (mask + 1));
    }
}
