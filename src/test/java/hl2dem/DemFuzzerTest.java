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

    private static byte[] magic() {
        return "PBDEMS2\0".getBytes(StandardCharsets.US_ASCII);
    }

    // 19-byte minimal legal demo: magic(8) + fileInfoOffset(4) + reserved(4) + DEM_STOP frame(3)
    private static byte[] minimalValidDemo() {
        byte[] buf = new byte[19];
        System.arraycopy(magic(), 0, buf, 0, 8);
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
            while (splicer.next() != null) {}
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
        Path p = writeTemp("magic_only.dem", magic());
        assertThrows(IOException.class, () -> runParser(p));
    }

    @Test
    void truncatedMidVarint_parserReturnsGracefully() throws IOException {
        byte[] bytes = new byte[17];
        System.arraycopy(magic(), 0, bytes, 0, 8);
        bytes[16] = (byte) 0x80; // continuation bit set with no following byte
        Path p = writeTemp("truncated_varint.dem", bytes);
        assertDoesNotThrow(() -> runParser(p));
    }

    @Test
    void oversizedFramePayload_parserReturnsGracefully() throws IOException {
        // cmd=3 (DEM_SyncTick), tick=0, size=16383 (0xFF 0x7F) exceeding file length
        byte[] bytes = new byte[20];
        System.arraycopy(magic(), 0, bytes, 0, 8);
        bytes[16] = 0x03;
        bytes[17] = 0x00;
        bytes[18] = (byte) 0xFF;
        bytes[19] = 0x7F;
        Path p = writeTemp("oversized.dem", bytes);
        assertDoesNotThrow(() -> runParser(p));
    }

    @Test
    void allZeroPayloadAfterHeader_parserReturnsGracefully() throws IOException {
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
        System.arraycopy(magic(), 0, bytes, 0, 8);
        Path p = writeTemp(seed + ".dem", bytes);
        // IOException from corrupt header bytes 8-15 is acceptable; RuntimeExceptions are not
        try (DemoFile demo = DemoFile.open(p)) {
            FrameSplicer splicer = new FrameSplicer(demo);
            while (splicer.next() != null) {}
        } catch (IOException ignored) {}
    }

    @ParameterizedTest
    @ValueSource(longs = { 100L, 200L, 300L, 400L, 500L })
    void bitFlipMutations_parserNeverHangs(long seed) throws IOException {
        Random rng = new Random(seed);
        byte[] bytes = minimalValidDemo().clone();
        bytes = java.util.Arrays.copyOf(bytes, 256);
        for (int i = 0; i < 10; i++) {
            int pos = 8 + rng.nextInt(bytes.length - 8);
            bytes[pos] ^= (byte) (1 << rng.nextInt(8));
        }
        Path p = writeTemp("bitflip_" + seed + ".dem", bytes);
        try (DemoFile demo = DemoFile.open(p)) {
            FrameSplicer splicer = new FrameSplicer(demo);
            while (splicer.next() != null) {}
        } catch (IOException ignored) {}
    }
}
