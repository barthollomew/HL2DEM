package hl2dem.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class PerfCounters {

    public final LongAdder bytesRead = new LongAdder();
    public final LongAdder ticksProcessed = new LongAdder();
    public final LongAdder framesProcessed = new LongAdder();
    public final AtomicInteger queueDepth = new AtomicInteger();

    public record Snapshot(long bytes, long ticks, long frames, int queue, long elapsedMs) {
        public double mbPerSec() {
            double secs = elapsedMs / 1000.0;
            return secs > 0 ? (bytes / 1_048_576.0) / secs : 0;
        }

        public long ticksPerSec() {
            double secs = elapsedMs / 1000.0;
            return secs > 0 ? (long) (ticks / secs) : 0;
        }
    }

    public Snapshot snapshot(long startMs) {
        return new Snapshot(
            bytesRead.sum(),
            ticksProcessed.sum(),
            framesProcessed.sum(),
            queueDepth.get(),
            System.currentTimeMillis() - startMs
        );
    }
}
