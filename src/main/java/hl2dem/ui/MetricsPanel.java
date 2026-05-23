package hl2dem.ui;

import hl2dem.util.PerfCounters;

public final class MetricsPanel {

    private static final int START_ROW = 3;

    public static void render(StringBuilder sb, PerfCounters.Snapshot snap) {
        row(sb, START_ROW,     "  Parse speed : ", String.format("%.1f MB/s", snap.mbPerSec()));
        row(sb, START_ROW + 1, "  Ticks/s     : ", String.format("%,d", snap.ticksPerSec()));
        row(sb, START_ROW + 2, "  Frames      : ", String.format("%,d", snap.frames()));
        row(sb, START_ROW + 3, "  Queue depth : ", snap.queue() + "/256");
        row(sb, START_ROW + 4, "  Bytes read  : ", formatBytes(snap.bytes()));
        row(sb, START_ROW + 5, "  Elapsed     : ", formatElapsed(snap.elapsedMs()));
    }

    private static void row(StringBuilder sb, int rowNum, String label, String value) {
        AnsiRenderer.moveTo(sb, rowNum, 1);
        AnsiRenderer.eraseLine(sb);
        AnsiRenderer.fg(sb, 36); // cyan label
        sb.append(label);
        AnsiRenderer.reset(sb);
        AnsiRenderer.fg(sb, 37); // white value
        sb.append(value);
        AnsiRenderer.reset(sb);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.2f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L) return String.format("%.1f MB", bytes / 1_048_576.0);
        return bytes + " B";
    }

    private static String formatElapsed(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private MetricsPanel() {}
}
