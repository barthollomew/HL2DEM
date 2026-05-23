package hl2dem.ui;

// ANSI escape code primitives. All methods append to a StringBuilder;
// the caller issues one print per frame to avoid tearing.
public final class AnsiRenderer {

    private static final String CSI = "\033[";

    public static void moveTo(StringBuilder sb, int row, int col) {
        sb.append(CSI).append(row).append(';').append(col).append('H');
    }

    public static void clearScreen(StringBuilder sb) {
        sb.append(CSI).append("2J");
    }

    public static void eraseLine(StringBuilder sb) {
        sb.append(CSI).append("2K");
    }

    public static void hideCursor() {
        System.out.print(CSI + "?25l");
        System.out.flush();
    }

    public static void showCursor() {
        System.out.print(CSI + "?25h");
        System.out.flush();
    }

    public static void bold(StringBuilder sb) {
        sb.append(CSI).append("1m");
    }

    public static void reset(StringBuilder sb) {
        sb.append(CSI).append("0m");
    }

    // Standard 8-color ANSI codes: 31=red, 32=green, 33=yellow, 34=blue,
    // 35=magenta, 36=cyan, 37=white.
    public static void fg(StringBuilder sb, int code) {
        sb.append(CSI).append(code).append('m');
    }

    // Append a field padded or truncated to exactly `width` chars.
    public static void field(StringBuilder sb, String value, int width) {
        if (value.length() >= width) {
            sb.append(value, 0, width);
        } else {
            sb.append(value);
            for (int i = value.length(); i < width; i++) sb.append(' ');
        }
    }

    public static void fieldRight(StringBuilder sb, String value, int width) {
        int pad = width - value.length();
        for (int i = 0; i < pad; i++) sb.append(' ');
        if (value.length() > width) sb.append(value, 0, width);
        else sb.append(value);
    }

    private AnsiRenderer() {}
}
