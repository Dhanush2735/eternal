package com.eternalengine.client.gui.paint;

import com.eternalengine.money.TradeView;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 24: Minecraft's trade screen with a townsperson, drawn around its slots: the villager's trade screen (its own
 * picture, from the game), the trades on the left as in Minecraft (what you give, an arrow, what you get; a cross
 * when it cannot be had now), what the trader says above it, and below it what the two slots do right now and the
 * money you carry. The game draws the slots and the items in them itself. The layout probe draws the same through
 * its own canvas, so the screen can be checked without a game.
 */
public final class TradeMenuPainter {

    public static final String VILLAGER = "minecraft:textures/gui/container/villager2.png";
    public static final String WIDGETS = "minecraft:textures/gui/widgets.png";
    public static final int W = 276;
    public static final int H = 166;
    /** Trades shown at once, as in Minecraft. */
    public static final int ROWS = 7;
    /** Minecraft's dark grey for labels on its screens. */
    public static final int LABEL = 0xFF404040;
    static final int LINK = 0xFF2B4FA0;
    static final int LINK_HOVER = 0xFF4F7FE0;
    static final int PANEL = 0xE0101014;
    static final int SAYS = 0xFFF4F4F4;
    static final int NAME = 0xFFFFFF55;
    static final int DIM = 0xFFA8A8B0;
    static final int STATUS = 0xFFFFE08A;

    /** What the screen remembers between frames. */
    public static final class State {
        public int scroll;
        public boolean dragging;
    }

    private TradeMenuPainter() {
    }

    public static int maxScroll(TradeView v) {
        return Math.max(0, v.rows.size() - ROWS);
    }

    /** The row index under the mouse, or -1. */
    public static int rowAt(TradeView v, State st, int left, int top, double mx, double my) {
        if (mx < left + 5 || mx >= left + 93 || my < top + 18 || my >= top + 18 + ROWS * 20) {
            return -1;
        }
        int idx = st.scroll + (int) ((my - top - 18) / 20);
        return idx < v.rows.size() ? idx : -1;
    }

    /**
     * Draws the screen around the slots ({@code left}, {@code top}: the corner of the 276 x 166 picture; {@code sw}
     * x {@code sh}: the screen). Returns the places to click: "row" (index), "back", "scroll".
     */
    public static List<TradePainter.Hot> paint(Canvas c, TradeView v, State st, int left, int top, int sw, int sh,
                                               int mx, int my, String tradesLabel, String inventoryLabel) {
        List<TradePainter.Hot> hots = new ArrayList<>();
        st.scroll = Math.max(0, Math.min(st.scroll, maxScroll(v)));
        c.blit(VILLAGER, left, top, 0, 0, W, H, 512, 256);

        // The labels where Minecraft has them.
        c.text(tradesLabel, left + 5 - c.width(tradesLabel) / 2 + 48, top + 6, LABEL, false);
        String title = c.fit(v.title == null || v.title.isEmpty() ? v.npcName : v.title, 166);
        c.text(title, left + 49 + W / 2 - c.width(title) / 2, top + 6, LABEL, false);
        c.text(inventoryLabel, left + 107, top + 72, LABEL, false);

        // Back to the talk, at the right of the inventory label.
        int room = W - 7 - (107 + c.width(inventoryLabel) + 10);
        String back = c.fit("‹ Back to " + v.npcName, room);
        if (c.width(back) > room || back.endsWith("…") && c.width("‹ Back") < room) {
            back = "‹ Back";
        }
        int bw = c.width(back);
        int bx = left + W - 7 - bw;
        int by = top + 72;
        boolean overBack = mx >= bx - 2 && mx < bx + bw + 2 && my >= by - 2 && my < by + 10;
        c.text(back, bx, by, overBack ? LINK_HOVER : LINK, false);
        if (overBack) {
            c.fill(bx, by + 9, bx + bw, by + 10, LINK_HOVER);
        }
        hots.add(new TradePainter.Hot(bx - 2, by - 2, bx + bw + 2, by + 10, "back", ""));

        // The trades: what you give, an arrow, what you get.
        for (int i = 0; i < ROWS; i++) {
            int idx = st.scroll + i;
            if (idx >= v.rows.size()) {
                break;
            }
            TradeView.Row r = v.rows.get(idx);
            int x = left + 5;
            int y = top + 18 + i * 20;
            boolean hover = mx >= x && mx < x + 88 && my >= y && my < y + 20;
            int tv = hover || idx == v.selected ? 86 : 66;
            c.blit(WIDGETS, x, y, 0, tv, 44, 20, 256, 256);
            c.blit(WIDGETS, x + 44, y, 200 - 44, tv, 44, 20, 256, 256);
            int iy = y + 1;
            c.stack(r.cost1(), left + 10, iy);
            c.stack(r.cost2(), left + 40, iy);
            if (r.moreCost()) {
                c.text("+", left + 56, iy + 4, NAME, true);
            }
            c.blit(VILLAGER, left + 60, iy + 3, r.open() ? 15 : 25, 171, 10, 9, 512, 256);
            c.stack(r.result(), left + 73, iy);
            if (r.morePay()) {
                c.text("+", left + 88, iy + 4, NAME, true);
            }
            hots.add(new TradePainter.Hot(x, y, x + 88, y + 20, "row", String.valueOf(idx)));
        }
        if (v.rows.isEmpty()) {
            List<String> none = wrap(c, "Nothing to trade now.", 80);
            for (int i = 0; i < none.size(); i++) {
                c.text(none.get(i), left + 9, top + 22 + i * 10, LABEL, false);
            }
        }

        // The scroller, as Minecraft draws it.
        int n = v.rows.size() + 1 - ROWS;
        if (n > 1) {
            int j = 139 - (27 + (n - 1) * 139 / n);
            int k = 1 + j / n + 139 / n;
            int off = Math.min(113, st.scroll * k);
            if (st.scroll == n - 1) {
                off = 113;
            }
            c.blit(VILLAGER, left + 94, top + 18 + off, 0, 199, 6, 27, 512, 256);
            hots.add(new TradePainter.Hot(left + 94, top + 18, left + 100, top + 158, "scroll", ""));
        } else {
            c.blit(VILLAGER, left + 94, top + 18, 6, 199, 6, 27, 512, 256);
        }

        // The picked trade cannot be had now (sold out today, or you have it): Minecraft's cross over the arrow.
        if (v.selected >= 0 && v.selected < v.rows.size() && !v.rows.get(v.selected).open()) {
            c.blit(VILLAGER, left + 182, top + 35, 311, 0, 28, 21, 512, 256);
        }

        // Above: what the trader says, and the date.
        int lines = Math.min(4, (top - 4) / 10);
        if (lines >= 1) {
            String date = v.dateLabel == null ? "" : v.dateLabel;
            int dateW = date.isEmpty() ? 0 : c.width(date) + 8;
            String who = v.npcName == null || v.npcName.isEmpty() ? "" : v.npcName + ": ";
            List<String> said = wrap(c, who + (v.says == null ? "" : v.says), W - 8, W - 8 - dateW);
            int shown = Math.max(1, Math.min(lines, said.size()));
            int y1 = top - 2;
            int y0 = y1 - (shown * 10 + 4);
            c.fill(left, y0, left + W, y1, PANEL);
            c.outline(left, y0, left + W, y1, 0xFF000000);
            for (int i = 0; i < shown && i < said.size(); i++) {
                String line = said.get(i);
                if (i == shown - 1 && said.size() > shown) {
                    line = c.fit(line + " …", W - 8 - (i == 0 ? dateW : 0));
                }
                int ty = y0 + 3 + i * 10;
                if (i == 0 && !who.isEmpty() && line.startsWith(who)) {
                    c.text(who, left + 4, ty, NAME, false);
                    c.text(line.substring(who.length()), left + 4 + c.width(who), ty, SAYS, false);
                } else {
                    c.text(line, left + 4, ty, SAYS, false);
                }
            }
            if (!date.isEmpty()) {
                c.text(date, left + W - 4 - c.width(date), y0 + 3, DIM, false);
            }
        }

        // Below: what the two slots do now, and the money you carry.
        String status = v.status == null ? "" : v.status;
        String purse = v.purse == null ? "" : v.purse;
        int below = Math.min(3, (sh - (top + H) - 6) / 10);
        if (below >= 1 && !(status.isEmpty() && purse.isEmpty())) {
            List<String> st2 = status.isEmpty() ? List.of() : wrap(c, status, W - 8);
            int statusLines = Math.min(st2.size(), Math.max(purse.isEmpty() ? below : below - 1, 1));
            int purseLines = !purse.isEmpty() && below > statusLines ? 1 : 0;
            int y0 = top + H + 2;
            int y1 = y0 + (statusLines + purseLines) * 10 + 4;
            c.fill(left, y0, left + W, y1, PANEL);
            c.outline(left, y0, left + W, y1, 0xFF000000);
            for (int i = 0; i < statusLines; i++) {
                String line = st2.get(i);
                if (i == statusLines - 1 && st2.size() > statusLines) {
                    line = c.fit(line + " …", W - 8);
                }
                c.text(line, left + 4, y0 + 3 + i * 10, STATUS, false);
            }
            if (purseLines > 0) {
                c.text(c.fit(purse, W - 8), left + 4, y0 + 3 + statusLines * 10, DIM, false);
            }
        } else if (!status.isEmpty()) {
            c.text(c.fit(status, 162), left + 107, top + 61, LABEL, false);
        }
        return hots;
    }

    /** Words wrapped to a width (the first line may be narrower). */
    public static List<String> wrap(Canvas c, String text, int width) {
        return wrap(c, text, width, width);
    }

    public static List<String> wrap(Canvas c, String text, int width, int firstWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            int w = out.isEmpty() ? firstWidth : width;
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (c.width(tryLine) <= w || line.length() == 0) {
                line.setLength(0);
                line.append(c.width(tryLine) <= w ? tryLine : c.fit(tryLine, w));
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(c.width(word) <= width ? word : c.fit(word, width));
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
