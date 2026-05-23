package hl2dem.frame;

import hl2dem.io.DemoFile;
import hl2dem.io.VarintReader;

public final class FrameSplicer {

    private final DemoFile demo;
    private long position;

    public FrameSplicer(DemoFile demo) {
        this.demo = demo;
        this.position = demo.dataStart;
    }

    // Returns null when DEM_Stop is encountered or end of file is reached.
    // On a read error, logs to stderr and returns null.
    public FrameHeader next() {
        if (position >= demo.segment.byteSize()) {
            return null;
        }
        try {
            long r1 = VarintReader.readVarint(demo.segment, position);
            int rawCmd = VarintReader.value(r1);
            position += VarintReader.bytesConsumed(r1);

            long r2 = VarintReader.readVarint(demo.segment, position);
            int tick = VarintReader.value(r2);
            position += VarintReader.bytesConsumed(r2);

            long r3 = VarintReader.readVarint(demo.segment, position);
            int size = VarintReader.value(r3);
            position += VarintReader.bytesConsumed(r3);

            if (size < 0) {
                System.err.println("warn: invalid negative payload size " + size + " at offset " + position);
                return null;
            }

            long payloadOffset = position;
            position += size;

            int cmd = DemoCommand.stripFlags(rawCmd);
            if (cmd == DemoCommand.STOP) {
                return null;
            }

            return new FrameHeader(rawCmd, tick, payloadOffset, size);
        } catch (IllegalStateException e) {
            System.err.println("warn: frame read error at offset " + position + ": " + e.getMessage());
            return null;
        }
    }
}
