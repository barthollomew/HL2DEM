package hl2dem.ui;

import hl2dem.analytics.BlindnessTracker;
import hl2dem.analytics.CrosshairEngine;
import hl2dem.entity.PlayerState;

import java.util.Arrays;
import java.util.Comparator;

public final class LeaderboardPanel {

    private static final int HEADER_ROW  = 11;
    private static final int DATA_ROW    = 12;
    private static final int MAX_DISPLAY = 20;

    private static final String COL_HEADER =
        "  #  Name              X          Y          Z       Acc%  Blind ms";

    public static void render(StringBuilder sb, PlayerState[] players,
                              CrosshairEngine crosshair, BlindnessTracker blind) {
        // Section header.
        AnsiRenderer.moveTo(sb, 10, 1);
        AnsiRenderer.eraseLine(sb);
        AnsiRenderer.bold(sb);
        AnsiRenderer.fg(sb, 33);
        sb.append("PLAYERS");
        AnsiRenderer.reset(sb);

        // Column header.
        AnsiRenderer.moveTo(sb, HEADER_ROW, 1);
        AnsiRenderer.eraseLine(sb);
        AnsiRenderer.fg(sb, 36);
        sb.append(COL_HEADER);
        AnsiRenderer.reset(sb);

        if (players == null || players.length == 0) {
            AnsiRenderer.moveTo(sb, DATA_ROW, 1);
            sb.append("  (no players)");
            return;
        }

        // Sort by accuracy descending.
        PlayerState[] sorted = players.clone();
        Arrays.sort(sorted, Comparator.comparingDouble(
            (PlayerState p) -> p == null ? 0.0 : crosshair.accuracy(p.entityId))
            .reversed());

        int count = Math.min(sorted.length, MAX_DISPLAY);
        for (int i = 0; i < count; i++) {
            PlayerState p = sorted[i];
            AnsiRenderer.moveTo(sb, DATA_ROW + i, 1);
            AnsiRenderer.eraseLine(sb);
            if (p == null) continue;

            float acc = crosshair.accuracy(p.entityId) * 100f;
            long blindMs = blind.lastBlindMs(p.entityId);

            AnsiRenderer.fg(sb, p.isAlive ? 32 : 31); // green=alive, red=dead
            AnsiRenderer.fieldRight(sb, String.valueOf(i + 1), 3);
            sb.append("  ");
            AnsiRenderer.field(sb, p.name.isEmpty() ? "?" : p.name, 16);
            sb.append("  ");
            AnsiRenderer.fieldRight(sb, String.format("%.1f", p.x), 8);
            sb.append("  ");
            AnsiRenderer.fieldRight(sb, String.format("%.1f", p.y), 8);
            sb.append("  ");
            AnsiRenderer.fieldRight(sb, String.format("%.1f", p.z), 8);
            sb.append("  ");
            AnsiRenderer.fieldRight(sb, String.format("%.1f", acc), 5);
            sb.append("  ");
            AnsiRenderer.fieldRight(sb, String.valueOf(blindMs), 7);
            AnsiRenderer.reset(sb);
        }

        // Blank out any leftover rows.
        for (int i = count; i < MAX_DISPLAY; i++) {
            AnsiRenderer.moveTo(sb, DATA_ROW + i, 1);
            AnsiRenderer.eraseLine(sb);
        }
    }

    private LeaderboardPanel() {}
}
