package hl2dem.export;

import hl2dem.entity.PlayerState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

// Writes one NDJSON object per player per tick to the output stream.
public final class JsonExporter implements Exporter {

    private final BufferedWriter writer;
    private final StringBuilder sb = new StringBuilder(256);

    public JsonExporter(OutputStream out) {
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 65536);
    }

    @Override
    public void write(PlayerState[] snapshot, int tick) throws IOException {
        if (snapshot == null) return;
        for (PlayerState p : snapshot) {
            if (p == null) continue;
            sb.setLength(0);
            sb.append("{\"tick\":").append(tick)
              .append(",\"entityId\":").append(p.entityId)
              .append(",\"steamId\":").append(p.steamId)
              .append(",\"name\":\"").append(escape(p.name)).append('"')
              .append(",\"x\":").append(round1(p.x))
              .append(",\"y\":").append(round1(p.y))
              .append(",\"z\":").append(round1(p.z))
              .append(",\"yaw\":").append(round1(p.yaw))
              .append(",\"pitch\":").append(round1(p.pitch))
              .append(",\"flash\":").append(round2(p.flashDuration))
              .append(",\"alive\":").append(p.isAlive)
              .append('}');
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

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static float round2(float v) {
        return Math.round(v * 100f) / 100f;
    }
}
