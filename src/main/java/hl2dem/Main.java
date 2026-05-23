package hl2dem;

import hl2dem.analytics.BlindnessTracker;
import hl2dem.analytics.CrosshairEngine;
import hl2dem.entity.EntityContext;
import hl2dem.entity.EntityDecoder;
import hl2dem.entity.PlayerState;
import hl2dem.export.CsvExporter;
import hl2dem.export.Exporter;
import hl2dem.export.JsonExporter;
import hl2dem.frame.DemoCommand;
import hl2dem.frame.FrameHeader;
import hl2dem.frame.FrameSplicer;
import hl2dem.frame.FrameUnpacker;
import hl2dem.io.DemoFile;
import hl2dem.ui.Dashboard;
import hl2dem.util.PerfCounters;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class Main {

    private static final int QUEUE_CAPACITY = 256;

    // Sentinel snapshot that signals the consumer thread to shut down.
    private static final PlayerState[] POISON = new PlayerState[0];

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: hl2dem <file.dem> [--out <file>] [--format json|csv]");
            System.exit(1);
        }

        Path demoPath  = Path.of(args[0]);
        String format  = "json";
        Path outPath   = null;

        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equals("--format")) format = args[i + 1];
            if (args[i].equals("--out"))    outPath = Path.of(args[i + 1]);
        }

        boolean toFile = outPath != null;

        PerfCounters    counters  = new PerfCounters();
        EntityContext   ctx       = new EntityContext();
        CrosshairEngine crosshair = new CrosshairEngine();
        BlindnessTracker blind    = new BlindnessTracker();
        Dashboard       dash      = new Dashboard();

        BlockingQueue<PlayerState[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        // Consumer thread: analytics + export.
        Exporter exporter = openExporter(format, outPath);
        Thread consumer = Thread.ofVirtual().name("consumer").start(() -> {
            try {
                while (true) {
                    PlayerState[] snap = queue.take();
                    if (snap == POISON) break;
                    // Run analytics off the hot path.
                    crosshair.process(snap);
                    blind.process(snap, snap.length > 0 && snap[0] != null
                        ? snap[0].lastUpdateTick : 0);
                    // Export if requested.
                    if (toFile && snap.length > 0 && snap[0] != null) {
                        exporter.write(snap, snap[0].lastUpdateTick);
                    }
                }
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
        });

        try (DemoFile demo = DemoFile.open(demoPath)) {
            dash.start(counters, ctx, crosshair, blind, demoPath);

            // Hot-path virtual thread: parse frames, decode entities, post snapshots.
            Thread parser = Thread.ofVirtual().name("parser").start(() -> {
                FrameSplicer  splicer  = new FrameSplicer(demo);
                FrameUnpacker unpacker = new FrameUnpacker();
                EntityDecoder decoder  = new EntityDecoder(ctx);
                long startMs = System.currentTimeMillis();

                FrameHeader h;
                while ((h = splicer.next()) != null) {
                    try {
                        byte[] payload = unpacker.unpack(demo.segment, h);
                        int cmd = DemoCommand.stripFlags(h.rawCmd());
                        decoder.handle(cmd, h.tick(), payload, unpacker.length());

                        counters.framesProcessed.increment();
                        counters.bytesRead.add(h.payloadSize());

                        if (decoder.tickComplete()) {
                            PlayerState[] snap = ctx.snapshot();
                            counters.ticksProcessed.increment();
                            counters.queueDepth.set(queue.size());
                            queue.put(snap);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("warn: frame error at tick " + h.tick()
                            + ": " + e.getMessage());
                    }
                }
                try { queue.put(POISON); } catch (InterruptedException ignored) {}
            });

            parser.join();
            consumer.join();

        } finally {
            dash.stop();
            exporter.flush();
            exporter.close();
        }

        System.out.printf("Done. Processed %,d ticks, %,d frames.%n",
            counters.ticksProcessed.sum(), counters.framesProcessed.sum());
    }

    private static Exporter openExporter(String format, Path outPath) throws IOException {
        OutputStream out = outPath != null
            ? Files.newOutputStream(outPath)
            : OutputStream.nullOutputStream();
        if ("csv".equalsIgnoreCase(format)) {
            return new CsvExporter(out);
        }
        return new JsonExporter(out);
    }
}
