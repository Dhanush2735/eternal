package com.eternalengine.client.gui.paint;

/**
 * Step 19c: the colours, the places to click and the buttons the screens share (the newspaper, the map and the history
 * book). Step 24: the trade screen itself is now Minecraft's trade screen ({@link TradeMenuPainter}).
 */
public final class TradePainter {

    public static final int BG = 0xF00E0E12;
    public static final int TEXT = 0xFFF4F4F4;
    public static final int GOLD = 0xFFFFD75E;
    public static final int NAME = 0xFFFFFF55;
    public static final int GRAY = 0xFFA8A8B0;
    public static final int DIM = 0xFF8A8A94;
    public static final int GREEN = 0xFF86E08A;
    public static final int RED = 0xFFFF8080;

    /** A place to click: an action and its argument. */
    public record Hot(int x0, int y0, int x1, int y1, String action, String arg) {
        public boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    private TradePainter() {
    }

    static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A button in the game's colours (greyed when it cannot be pressed). */
    public static void button(Canvas c, int x, int y, int w, int h, String label, boolean enabled, boolean hover) {
        c.fill(x, y, x + w, y + h, enabled ? (hover ? 0xFF5A5A68 : 0xFF3C3C46) : 0xFF26262C);
        c.outline(x, y, x + w, y + h, 0xFF000000);
        if (enabled) {
            c.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        }
        String l = c.fit(label, w - 6);
        c.text(l, x + (w - c.width(l)) / 2, y + (h - 8) / 2, enabled ? TEXT : DIM, enabled);
    }
}
