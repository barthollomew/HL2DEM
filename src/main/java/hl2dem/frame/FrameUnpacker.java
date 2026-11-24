package hl2dem.frame;

import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class FrameUnpacker {

    private byte[] compressed = new byte[1 << 17];
    private byte[] scratch = new byte[1 << 20];
    private int unpackedLen;

    // Returns the scratch buffer. Valid bytes are [0 .. length()-1).
    // The caller must not retain this reference across the next unpack() call.
    public byte[] unpack(MemorySegment seg, FrameHeader header) {
        int size = header.payloadSize();
        long offset = header.payloadOffset();

        if (DemoCommand.isCompressed(header.rawCmd())) {
            if (size > compressed.length) {
                compressed = new byte[size];
            }
            MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, offset, compressed, 0, size);

            try {
                int needed = Snappy.uncompressedLength(compressed, 0, size);
                if (needed > scratch.length) {
                    scratch = new byte[needed];
                }
                unpackedLen = Snappy.uncompress(compressed, 0, size, scratch, 0);
            } catch (IOException e) {
                System.err.println("warn: Snappy decompress failed at tick "
                    + header.tick() + ": " + e.getMessage());
                unpackedLen = 0;
            }
        } else {
            if (size > scratch.length) {
                scratch = new byte[size];
            }
            MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, offset, scratch, 0, size);
            unpackedLen = size;
        }
        return scratch;
    }

    public int length() {
        return unpackedLen;
    }
}
