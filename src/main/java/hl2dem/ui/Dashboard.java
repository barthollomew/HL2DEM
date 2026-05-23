package hl2dem.ui;

import hl2dem.analytics.BlindnessTracker;
import hl2dem.analytics.CrosshairEngine;
import hl2dem.entity.EntityContext;
import hl2dem.entity.PlayerState;
import hl2dem.util.PerfCounters;

import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Dashboard {

    private final ScheduledExecutorService timer =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ui-timer");
            t.setDaemon(true);
            return t;
        });

    private final StringBuilder frame = new StringBuilder(8192);

    private PerfCounters counters;
    private EntityContext entityContext;
    private CrosshairEngine crosshair;
    private BlindnessTracker blind;
    private long startMs;
    private String filename;

    private ScheduledFuture<?> task;

    public void start(PerfCounters counters, EntityContext ctx,
                      CrosshairEngine crosshair, BlindnessTracker blind,
                      Path demoPath) {
        this.counters = counters;
        this.entityContext = ctx;
        this.crosshair = crosshair;
        this.blind = blind;
        this.startMs = System.currentTimeMillis();
        this.filename = demoPath.getFileName().toString();

        AnsiRenderer.hideCursor();
        // Full clear once at startup.
        StringBuilder init = new StringBuilder();
        AnsiRenderer.clearScreen(init);
        AnsiRenderer.moveTo(init, 1, 1);
        System.out.print(init);
        System.out.flush();

        task = timer.scheduleAtFixedRate(this::render, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void render() {
        try {
            PerfCounters.Snapshot snap = counters.snapshot(startMs);

            frame.setLength(0);
            // Title bar.
            AnsiRenderer.moveTo(frame, 1, 1);
            AnsiRenderer.eraseLine(frame);
            AnsiRenderer.bold(frame);
            AnsiRenderer.fg(frame, 33);
            frame.append("HL2DEM");
            AnsiRenderer.reset(frame);
            frame.append("  |  ").append(filename)
                 .append("  |  Tick: ").append(String.format("%,d", snap.ticks()))
                 .append("  |  ").append(formatElapsed(snap.elapsedMs()));

            // Separator.
            AnsiRenderer.moveTo(frame, 2, 1);
            AnsiRenderer.eraseLine(frame);
            AnsiRenderer.fg(frame, 34);
            frame.append("--------------------------------------------------------------------");
            AnsiRenderer.reset(frame);

            // Performance metrics section header.
            AnsiRenderer.moveTo(frame, 9, 1);
            AnsiRenderer.eraseLine(frame);
            AnsiRenderer.bold(frame);
            AnsiRenderer.fg(frame, 33);
            frame.append("PERFORMANCE");
            AnsiRenderer.reset(frame);

            MetricsPanel.render(frame, snap);

            // Player leaderboard.
            PlayerState[] players = entityContext.snapshot();
            LeaderboardPanel.render(frame, players, crosshair, blind);

            System.out.print(frame);
            System.out.flush();
        } catch (Exception ignored) {
            // Never let a render exception kill the UI thread.
        }
    }

    public void stop() {
        if (task != null) task.cancel(false);
        timer.shutdownNow();
        // Restore terminal.
        StringBuilder cleanup = new StringBuilder();
        AnsiRenderer.moveTo(cleanup, 50, 1);
        AnsiRenderer.reset(cleanup);
        System.out.print(cleanup);
        AnsiRenderer.showCursor();
        System.out.println();
    }

    private static String formatElapsed(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
