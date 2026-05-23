package hl2dem;

import hl2dem.frame.FrameSplicer;
import hl2dem.io.DemoFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
class DemFuzzerTest {

    @TempDir
    Path tempDir;

    // 8-byte PBDEMS2\0 magic
    private static byte[] magic() {
        return "PBDEMS2\0".getBytes(StandardCharsets.US_ASCII);
    }

    // 19-byte minimal legal demo: magic(8) + fileInfoOffset(4) + reserved(4) + DEM_STOP frame(3)
    // DEM_STOP frame = varint(cmd=0) + varint(tick=0) + varint(size=0) = 3 zero bytes
    private static byte[] minimalValidDemo() {
        byte[] buf = new byte[19];
        System.arraycopy(magic(), 0, buf, 0, 8);
        // fileInfoOffset at bytes 8-11 = 0 (LE int32)
        // reserved at bytes 12-15 = 0
        // DEM_STOP: cmd=0, tick=0, size=0 — three single-byte varints at bytes 16-18
        return buf;
    }

    private Path writeTemp(String name, byte[] bytes) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, bytes);
        return f;
    }

    private void runParser(Path p) throws IOException {
        try (DemoFile demo = DemoFile.open(p)) {
            FrameSplicer splicer = new FrameSplicer(demo);
            while (splicer.next() != null) { /* exhaust frames */ }
        }
    }

    @Test
    void validMinimalDemo_parsesCleanly() throws IOException {
        Path p = writeTemp("valid.dem", minimalValidDemo());
        assertDoesNotThrow(() -> runParser(p));
    }

    @Test
    void invalidMagic_throwsIOException() throws IOException {
        byte[] bytes = minimalValidDemo();
        bytes[0] = 'X';
        Path p = writeTemp("bad_magic.dem", bytes);
        assertThrows(IOException.class, () -> runParser(p));
    }

    @Test
    void emptyFile_throwsIOException() throws IOException {
        Path p = writeTemp("empty.dem", new byte[0]);
        assertThrows(IOException.class, () -> runParser(p));
    }

    @Test
    void twoBytes_throwsIOException() throws IOException {
        Path p = writeTemp("tiny.dem", new byte[]{ 0x50, 0x42 });
        assertThrows(IOException.class, () -> runParser(p));
    }

    @Test
    void truncatedAfterMagic_throwsIOException() throws IOException {
        // 8-byte magic only — fileInfoOffset bytes are missing
        Path p = writeTemp("magic_only.dem", magic());
        assertThrows(IOException.class, () -> runParser(p));
    }

    @Test
    void truncatedMidVarint_parserReturnsGracefully() throws IOException {
        // Valid 16-byte header, then a varint byte with continuation bit set (0x80) but no next byte
        byte[] bytes = new byte[17];
        System.arraycopy(magic(), 0, bytes, 0, 8);
        bytes[16] = (byte) 0x80;
        Path p = writeTemp("truncated_varint.dem", bytes);
        // FrameSplicer catches IllegalStateException from VarintReader and returns null
        assertDoesNotThrow(() -> runParser(p));
    }

    @Test
    void oversizedFramePayload_parserReturnsGracefully() throws IOException {
        // cmd=3 (DEM_SyncTick), tick=0, size=0x3FFF (16383) — payload far exceeds file size
        // Encodes size as two-byte varint: 0xFF 0x7F
        byte[] bytes = new byte[20];
        System.arraycopy(magic(), 0, bytes, 0, 8);
        bytes[16] = 0x03; // cmd varint
        bytes[17] = 0x00; // tick varint
        bytes[18] = (byte) 0xFF; // size varint byte 1 (continuation)
        bytes[19] = 0x7F;        // size varint byte 2 = 16383
        // FrameSplicer reads the header, advances position by 16383+20, next call returns null
        Path p = writeTemp("oversized.dem", bytes);
        assertDoesNotThrow(() -> runParser(p));
    }

    @Test
    void allZeroPayloadAfterHeader_parserReturnsGracefully() throws IOException {
        // Realistic corruption: valid magic + 1 KB of zeros
        byte[] bytes = new byte[1024];
        System.arraycopy(magic(), 0, bytes, 0, 8);
        Path p = writeTemp("all_zeros.dem", bytes);
        assertDoesNotThrow(() -> runParser(p));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 42L, 12345L, 999_999L })
    void randomMutations_parserNeverHangs(long seed) throws IOException {
        Random rng = new Random(seed);
        byte[] bytes = new byte[512];
        rng.nextBytes(bytes);
        System.arraycopy(magic(), 0, bytes, 0, 8); // keep magic so DemoFile.open() succeeds
        Path p = writeTemp(seed + ".dem", bytes);
        // IOException from corrupt header bytes 8-15 is acceptable; RuntimeExceptions are not
        try (DemoFile demo = DemoFile.open(p)) {
            FrameSplicer splicer = new FrameSplicer(demo);
            while (splicer.next() != null) { /* exhaust */ }
        } catch (IOException ignored) {
            // Acceptable: corrupt fileInfoOffset or reserved bytes may cause IOException
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 100L, 200L, 300L, 400L, 500L })
    void bitFlipMutations_parserNeverHangs(long seed) throws IOException {
        Random rng = new Random(seed);
        byte[] bytes = minimalValidDemo().clone();
        // Expand to 256 bytes with realistic padding
        bytes = java.util.Arrays.copyOf(bytes, 256);
        // Apply 10 random bit-flips beyond the magic header
        for (int i = 0; i < 10; i++) {
            int pos = 8 + rng.nextInt(bytes.length - 8);
            bytes[pos] ^= (byte) (1 << rng.nextInt(8));
        }
        Path p = writeTemp("bitflip_" + seed + ".dem", bytes);
        try (DemoFile demo = DemoFile.open(p)) {
            FrameSplicer splicer = new FrameSplicer(demo);
            while (splicer.next() != null) { /* exhaust */ }
        } catch (IOException ignored) { }
    }
}
