package hl2dem.export;

import hl2dem.entity.PlayerState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class CsvExporter implements Exporter {

    private static final String HEADER =
        "tick,entityId,steamId,name,x,y,z,yaw,pitch,flash,alive";

    private final BufferedWriter writer;
    private final StringBuilder sb = new StringBuilder(128);

    public CsvExporter(OutputStream out) throws IOException {
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 65536);
        writer.write(HEADER);
        writer.newLine();
    }

    @Override
    public void write(PlayerState[] snapshot, int tick) throws IOException {
        if (snapshot == null) return;
        for (PlayerState p : snapshot) {
            if (p == null) continue;
            sb.setLength(0);
            sb.append(tick).append(',')
              .append(p.entityId).append(',')
              .append(p.steamId).append(',')
              .append(csvEscape(p.name)).append(',')
              .append(fmt(p.x)).append(',')
              .append(fmt(p.y)).append(',')
              .append(fmt(p.z)).append(',')
              .append(fmt(p.yaw)).append(',')
              .append(fmt(p.pitch)).append(',')
              .append(fmt2(p.flashDuration)).append(',')
              .append(p.isAlive ? 1 : 0);
            writer.write(sb.toString());
            writer.newLine();
        }
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String fmt(float v) {
        return String.format("%.1f", v);
    }

    private static String fmt2(float v) {
        return String.format("%.2f", v);
    }
}
