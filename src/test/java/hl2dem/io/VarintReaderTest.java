package hl2dem.io;

import hl2dem.frame.DemoCommand;
import hl2dem.frame.FrameHeader;
import hl2dem.frame.FrameSplicer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Tests VarintReader behaviour through the FrameSplicer, avoiding direct MemorySegment
// references in the test source (MemorySegment is a preview API in Java 21).
@Timeout(10)
class VarintReaderTest {

    @TempDir
    Path tempDir;

    private static byte[] magic() {
        return "PBDEMS2\0".getBytes(StandardCharsets.US_ASCII);
    }

    // Builds a 19-byte minimal demo:
    // magic(8) + fileInfoOffset(4) + reserved(4) + three varint bytes
    private static byte[] demoWith(byte... frameBytes) {
        byte[] buf = new byte[16 + frameBytes.length];
        System.arraycopy(magic(), 0, buf, 0, 8);
        System.arraycopy(frameBytes, 0, buf, 16, frameBytes.length);
        return buf;
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, bytes);
        return f;
    }

    // Parses the first non-STOP frame from a demo file.
    private FrameHeader firstFrame(Path p) throws IOException {
        try (DemoFile demo = DemoFile.open(p)) {
            return new FrameSplicer(demo).next();
        }
    }

    // --- single-byte varint (cmd < 128 encodes in one byte) ---

    @Test
    void singleByteVarint_cmd3_tick0_size0() throws IOException {
        // cmd=3 (one byte), tick=0, size=0 → DEM_SyncTick with no payload
        Path p = write("sync.dem", demoWith((byte) 0x03, (byte) 0x00, (byte) 0x00,
                                             (byte) 0x00, (byte) 0x00, (byte) 0x00));
        FrameHeader h = firstFrame(p);
        assertNotNull(h);
        assertEquals(3, DemoCommand.stripFlags(h.rawCmd()));
        assertEquals(0, h.tick());
        assertEquals(0, h.payloadSize());
    }

    @Test
    void singleByteVarint_tick127() throws IOException {
        // cmd=3, tick=0x7F (127 — max single-byte varint), size=0
        Path p = write("tick127.dem", demoWith((byte) 0x03, (byte) 0x7F, (byte) 0x00,
                                                (byte) 0x00, (byte) 0x00, (byte) 0x00));
        FrameHeader h = firstFrame(p);
        assertNotNull(h);
        assertEquals(127, h.tick());
    }

    // --- two-byte varint (values 128–16383) ---

    @Test
    void twoByteVarint_tick128() throws IOException {
        // 128 encodes as 0x80 0x01
        Path p = write("tick128.dem", demoWith((byte) 0x03, (byte) 0x80, (byte) 0x01, (byte) 0x00,
                                                (byte) 0x00, (byte) 0x00, (byte) 0x00));
        FrameHeader h = firstFrame(p);
        assertNotNull(h);
        assertEquals(128, h.tick());
    }

    @Test
    void twoByteVarint_tick300() throws IOException {
        // 300 = 0x12C → low 7 bits = 0x2C | 0x80, high 7 bits = 0x02
        Path p = write("tick300.dem", demoWith((byte) 0x03, (byte) 0xAC, (byte) 0x02, (byte) 0x00,
                                                (byte) 0x00, (byte) 0x00, (byte) 0x00));
        FrameHeader h = firstFrame(p);
        assertNotNull(h);
        assertEquals(300, h.tick());
    }

    // --- truncated varint → parser returns null gracefully ---

    @Test
    void truncatedVarint_returnsNull() throws IOException {
        // 0x80 with continuation bit but no next byte → ISE caught by FrameSplicer
        Path p = write("trunc.dem", demoWith((byte) 0x80));
        assertNull(firstFrame(p));
    }

    // --- negative size varint → parser returns null, position doesn't underflow ---

    @Test
    void negativeSize_returnsNullNoUnderflow() throws IOException {
        // cmd=3, tick=0, size = 0xFF 0xFF 0xFF 0xFF 0x0F (0xFFFFFFFF = -1 as signed int)
        // Before the FrameSplicer guard, position would decrease causing IOOBE.
        Path p = write("negsize.dem", demoWith(
            (byte) 0x03, (byte) 0x00,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0F,
            // DEM_STOP after (will not be reached if guard fires correctly)
            (byte) 0x00, (byte) 0x00, (byte) 0x00
        ));
        // Must return null and not throw IndexOutOfBoundsException
        assertDoesNotThrow(() -> {
            try (DemoFile demo = DemoFile.open(p)) {
                FrameSplicer splicer = new FrameSplicer(demo);
                while (splicer.next() != null) {}
            }
        });
    }
}
