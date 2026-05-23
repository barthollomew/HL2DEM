package hl2dem.io;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DemoFile implements AutoCloseable {

    private static final byte[] MAGIC = "PBDEMS2\0".getBytes(StandardCharsets.US_ASCII);

    public final MemorySegment segment;
    public final int fileInfoOffset;
    public final long dataStart;

    private final Arena arena;

    private DemoFile(MemorySegment segment, int fileInfoOffset, Arena arena) {
        this.segment = segment;
        this.fileInfoOffset = fileInfoOffset;
        this.dataStart = 16; // 8-byte magic + 4-byte fileInfoOffset + 4-byte reserved
        this.arena = arena;
    }

    public static DemoFile open(Path path) throws IOException {
        Arena arena = Arena.ofConfined();
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            MemorySegment seg = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            channel.close();

            checkMagic(seg, path);

            // fileInfoOffset: int32 little-endian at bytes 8-11
            int fileInfoOffset =
                (seg.get(ValueLayout.JAVA_BYTE, 8) & 0xFF)
                | ((seg.get(ValueLayout.JAVA_BYTE, 9) & 0xFF) << 8)
                | ((seg.get(ValueLayout.JAVA_BYTE, 10) & 0xFF) << 16)
                | ((seg.get(ValueLayout.JAVA_BYTE, 11) & 0xFF) << 24);

            return new DemoFile(seg, fileInfoOffset, arena);
        } catch (Exception e) {
            arena.close();
            if (e instanceof IOException ioe) throw ioe;
            throw new IOException("Failed to open demo file: " + path, e);
        }
    }

    private static void checkMagic(MemorySegment seg, Path path) throws IOException {
        if (seg.byteSize() < MAGIC.length) {
            throw new IOException("File too small to be a demo: " + path);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, i) != MAGIC[i]) {
                throw new IOException(
                    "Invalid demo file magic (expected PBDEMS2\\0): " + path);
            }
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
