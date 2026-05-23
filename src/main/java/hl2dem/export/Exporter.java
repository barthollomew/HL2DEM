package hl2dem.export;

import hl2dem.entity.PlayerState;
import java.io.IOException;

public interface Exporter {
    void write(PlayerState[] snapshot, int tick) throws IOException;
    void flush() throws IOException;
    void close() throws IOException;
}
