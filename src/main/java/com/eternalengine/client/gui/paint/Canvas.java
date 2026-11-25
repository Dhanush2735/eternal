package com.eternalengine.client.gui.paint;

/**
 * Steps 19c to 19e: what the trade screen, the newspaper and the map draw with. The game draws through it with
 * Minecraft's font and items; the layout probe draws through it into a picture, so the screens can be checked without
 * a game. Coordinates are GUI units.
 */
public interface Canvas {

    int LINE_H = 10;

    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String s, int x, int y, int argb, boolean shadow);

    /** Text drawn {@code scale} times bigger (mastheads, titles). */
    void textScaled(String s, int x, int y, int argb, float scale);

    int width(String s);

    /** An item's picture (registry name), 16 units square times {@code scale}, with its count when above one. */
    void item(String id, int count, int x, int y, int scale);

    /**
     * Step 24: a part of a picture of the game ({@code texture} as "namespace:path"): {@code w} x {@code h} units from
     * ({@code u}, {@code v}) of a {@code texW} x {@code texH} picture. Without the picture: a grey box.
     */
    default void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH) {
        fill(x, y, x + w, y + h, 0xFF8B8B8B);
    }

    /** Step 24: an item picture with its count and data (a coin of its money), as the trade screen sends it. */
    default void stack(com.eternalengine.money.TradeView.Stack s, int x, int y) {
        if (s != null && !s.isEmpty()) {
            item(s.item(), s.count(), x, y, 1);
        }
    }

    /** Bold text: drawn twice, a unit apart. */
    default void bold(String s, int x, int y, int argb) {
        text(s, x, y, argb, false);
        text(s, x + 1, y, argb, false);
    }

    default void outline(int x0, int y0, int x1, int y1, int argb) {
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
        fill(x0, y0, x0 + 1, y1, argb);
        fill(x1 - 1, y0, x1, y1, argb);
    }

    /** Text cut to a width, with "…" when cut. */
    default String fit(String s, int w) {
        if (s == null) {
            return "";
        }
        if (width(s) <= w) {
            return s;
        }
        String e = "…";
        int n = s.length();
        while (n > 0 && width(s.substring(0, n) + e) > w) {
            n--;
        }
        return s.substring(0, n) + e;
    }
}
