package com.eternalengine.client.gui.paint;

import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.news.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 19e: the map screen from the approved preview: towns (big towns bold), dashed roads, where you stand (a white
 * square), a coloured news mark for each paper's day, a dashed red circle round the town picked, a compass, a scale
 * bar and the title. Clicking a town shows what your papers said about it; "Add to Xaero's map" makes a waypoint.
 */
public final class MapPainter {

    static final int[] DAY_COLOURS = {0xFF7F8C8D, 0xFFC0392B, 0xFFE67E22, 0xFF27AE60, 0xFF2E86C1, 0xFF8E44AD,
            0xFF16A085, 0xFFD35400};
    static final int PARCHMENT = 0xFFEFE3C2;
    static final int INK = 0xFF3B2A14;
    static final int ROAD = 0xFF9A6B3F;

    /** What the screen remembers between frames. */
    public static final class State {
        public String selected = "";
        public String message = "";
    }

    private MapPainter() {
    }

    public static List<TradePainter.Hot> paint(Canvas c, MapView v, State st, int sw, int sh, int mx, int my,
                                               boolean xaero) {
        List<TradePainter.Hot> hots = new ArrayList<>();
        double midLat = (v.minLat + v.maxLat) / 2.0d;
        double kmW = Math.max(1e-6, (v.maxLon - v.minLon) * 111.32d * Math.cos(Math.toRadians(midLat)));
        double kmH = Math.max(1e-6, (v.maxLat - v.minLat) * 110.57d);
        double aspect = kmW / kmH;
        // Wide screens: the notes beside the map; narrow ones: under it.
        int mhSide = sh - 12 - 16 - 6;
        int mwSide = (int) Math.round(mhSide * aspect);
        boolean side = sw - 12 - mwSide - 18 >= 150;
        int mw, mh, w, h, sideW = 0;
        int below = 4 * Canvas.LINE_H + 26;
        if (side) {
            mh = mhSide;
            mw = mwSide;
            sideW = Math.min(210, sw - 12 - mw - 18);
            w = mw + 18 + sideW;
            h = 16 + mh + 6;
        } else {
            w = Math.min(440, sw - 12);
            mw = w - 12;
            mh = (int) Math.round(mw / aspect);
            int maxMh = sh - 12 - 16 - below;
            if (mh > maxMh) {
                mh = Math.max(60, maxMh);
                mw = (int) Math.round(mh * aspect);
                w = Math.max(mw + 12, Math.min(sw - 12, 240));
            }
            h = 16 + mh + below;
        }
        int x0 = (sw - w) / 2, y0 = Math.max(4, (sh - h) / 2), x1 = x0 + w, y1 = y0 + h;
        c.fill(x0, y0, x1, y1, TradePainter.BG);
        c.outline(x0, y0, x1, y1, 0xFF000000);
        c.text(c.fit(v.title, w - 60), x0 + 6, y0 + 4, TradePainter.NAME, true);
        String close = "Close ×";
        c.text(close, x1 - 6 - c.width(close), y0 + 4, TradePainter.GOLD, false);
        hots.add(new TradePainter.Hot(x1 - 8 - c.width(close), y0, x1, y0 + 14, "close", ""));

        int ax = x0 + 6, ay = y0 + 16;
        c.fill(ax, ay, ax + mw, ay + mh, PARCHMENT);
        c.outline(ax - 1, ay - 1, ax + mw + 1, ay + mh + 1, 0xFF6B4A2B);
        Box box = new Box(v, ax, ay, mw, mh);
        List<int[]> taken = new ArrayList<>();

        // Title, compass and scale first: labels keep clear of them. The title goes in the top corner (or the bottom
        // right) that hides the fewest towns; the compass in the other top corner.
        String t = v.title.replaceFirst("^Map of (the )?", "").toUpperCase(Locale.ROOT);
        int tw = Math.min(mw / 2 + 20, Math.max(c.width(t) + 1, c.width(v.subtitle)) + 8);
        int[][] corners = {{ax + 3, ay + 3}, {ax + mw - 3 - tw, ay + 3}, {ax + mw - 3 - tw, ay + mh - 3 - 22}};
        int best = 0, fewest = Integer.MAX_VALUE;
        for (int k = 0; k < corners.length; k++) {
            int n = 0;
            for (MapView.Town town : v.towns) {
                int px = box.x(town.lon()), py = box.y(town.lat());
                if (px >= corners[k][0] - 6 && px <= corners[k][0] + tw + 6 && py >= corners[k][1] - 6
                        && py <= corners[k][1] + 28) {
                    n += town.major() ? 3 : 2;
                }
            }
            if (n < fewest) {
                fewest = n;
                best = k;
            }
        }
        int tx = corners[best][0], ty = corners[best][1];
        c.fill(tx, ty, tx + tw, ty + 22, 0xE0EFE3C2);
        c.outline(tx, ty, tx + tw, ty + 22, 0xFF6B4A2B);
        c.bold(c.fit(t, tw - 8), tx + 4, ty + 3, INK);
        c.text(c.fit(v.subtitle, tw - 8), tx + 4, ty + 12, 0xFF6B5636, false);
        taken.add(new int[]{tx, ty, tx + tw, ty + 22});
        int nx = best == 1 ? ax + 10 : ax + mw - 10, ny = ay + 6;
        for (int i = 0; i < 6; i++) {
            c.fill(nx - i / 2, ny + 4 + i, nx + 1 + i / 2, ny + 5 + i, INK);
        }
        c.text("N", nx - c.width("N") / 2 + 1, ny - 5 + 1, INK, false);
        taken.add(new int[]{nx - 6, ny - 5, nx + 6, ny + 11});
        int[] steps = {10, 25, 50, 100, 200, 500, 1000};
        int km = 100;
        for (int s : steps) {
            if (s * mw / kmW <= mw / 4.0d) {
                km = s;
            }
        }
        int barW = (int) Math.round(km * mw / kmW);
        int bx = ax + 6, by = ay + mh - 9;
        String kmText = km + " km";
        taken.add(new int[]{bx, by - 4, bx + barW + 4 + c.width(kmText), by + 4});

        // Roads.
        for (double[] r : v.roads) {
            for (int i = 0; i + 3 < r.length; i += 2) {
                dashed(c, box, box.x(r[i + 1]), box.y(r[i]), box.x(r[i + 3]), box.y(r[i + 2]), ROAD, 3, 2);
            }
        }
        c.fill(bx, by, bx + barW, by + 3, INK);
        c.fill(bx + barW / 2, by + 1, bx + barW, by + 2, PARCHMENT);
        c.text(kmText, bx + barW + 4, by - 3, INK, false);

        List<Integer> days = v.markDates();
        // Dots first (they are in the way of labels), then the labels: the town picked, towns in the news, big
        // towns, the rest.
        List<MapView.Town> order = new ArrayList<>();
        List<String> named = new ArrayList<>();
        for (MapView.Town town : v.towns) {
            int px = box.x(town.lon()), py = box.y(town.lat());
            if (!box.inside(px, py)) {
                continue;
            }
            int r = town.major() ? 2 : 1;
            c.fill(px - r, py - r, px + r + 1, py + r + 1, INK);
            taken.add(new int[]{px - r - 1, py - r - 1, px + r + 2, py + r + 2});
            order.add(town);
            named.add(town.name().toLowerCase(Locale.ROOT));
            hots.add(new TradePainter.Hot(px - 5, py - 5, px + 6, py + 6, "select", town.name()));
        }
        order.sort((a, b) -> Integer.compare(rank(v, st, b), rank(v, st, a)));
        for (MapView.Town town : order) {
            int px = box.x(town.lon()), py = box.y(town.lat());
            drawMarks(c, v.marksFor(town.name()), days, px, py);
            String label = town.name();
            int lw = c.width(label) + (town.major() ? 1 : 0);
            int[] spot = place(taken, box, px, py, lw);
            if (spot == null) {
                continue;
            }
            if (town.major()) {
                c.bold(label, spot[0], spot[1], INK);
            } else {
                c.text(label, spot[0], spot[1], INK, false);
            }
            taken.add(new int[]{spot[0], spot[1], spot[0] + lw, spot[1] + 8});
            hots.add(new TradePainter.Hot(spot[0], spot[1] - 1, spot[0] + lw, spot[1] + 9, "select", town.name()));
        }
        // Marks at places that are not towns on the map (an event's place).
        List<String> drawn = new ArrayList<>();
        for (MapView.Mark k : v.marks) {
            String key = k.name().toLowerCase(Locale.ROOT);
            if (named.contains(key) || drawn.contains(key)) {
                continue;
            }
            int px = box.x(k.lon()), py = box.y(k.lat());
            if (!box.inside(px, py)) {
                continue;
            }
            drawn.add(key);
            drawMarks(c, v.marksFor(k.name()), days, px, py);
            hots.add(new TradePainter.Hot(px - 5, py - 5, px + 6, py + 6, "select", k.name()));
        }

        // Where you stand.
        if (!Double.isNaN(v.youLat)) {
            int px = box.x(v.youLon), py = box.y(v.youLat);
            if (box.inside(px, py)) {
                c.fill(px - 3, py - 3, px + 4, py + 4, 0xFF000000);
                c.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
                int[] spot = place(taken, box, px, py, c.width("you"));
                if (spot != null) {
                    c.text("you", spot[0], spot[1], 0xFF000000, false);
                    taken.add(new int[]{spot[0], spot[1], spot[0] + c.width("you"), spot[1] + 8});
                }
            }
        }

        // The town picked: a dashed red circle.
        double[] sel = position(v, st.selected);
        if (sel != null) {
            circle(c, box, box.x(sel[1]), box.y(sel[0]), 8, 0xFFD02020);
        }

        // The notes: the legend, what the papers said about the town picked, and the buttons.
        int nx0 = side ? ax + mw + 10 : ax;
        int nw = side ? sideW - 4 : w - 12;
        int ly = side ? ay + 1 : ay + mh + 4;
        int bottomY = y1 - 20;
        List<String[]> notes = new ArrayList<>(); // text, colour as hex, or "#day:<i>" for a legend line
        if (days.isEmpty()) {
            for (List<Rich.Word> l : Rich.wrap(c, "No news marks yet. Buy newspapers: each one marks its places here.", nw)) {
                notes.add(new String[]{join(l), "gray"});
            }
        } else {
            for (List<Rich.Word> l : Rich.wrap(c, "News marks from your papers:", nw)) {
                notes.add(new String[]{join(l), "gray"});
            }
            StringBuilder row = new StringBuilder();
            int rowW = 0;
            for (int i = 0; i < days.size(); i++) {
                String d = KnowledgeDate.shortLabel(days.get(i));
                int dw = 10 + c.width(d) + 6;
                if (side || rowW + dw > nw) {
                    if (row.length() > 0) {
                        notes.add(new String[]{row.toString(), "days"});
                    }
                    row = new StringBuilder();
                    rowW = 0;
                }
                row.append(row.length() == 0 ? "" : "|").append(i);
                rowW += dw;
            }
            if (row.length() > 0) {
                notes.add(new String[]{row.toString(), "days"});
            }
        }
        notes.add(new String[]{"", "gray"});
        if (st.selected.isBlank()) {
            for (List<Rich.Word> l : Rich.wrap(c, "Click a town to see what your papers said about it.", nw)) {
                notes.add(new String[]{join(l), "text"});
            }
        } else {
            List<MapView.Mark> ms = v.marksFor(st.selected);
            String about = ms.isEmpty() ? st.selected + ": a town on your map. No news about it in your papers." : "";
            if (!ms.isEmpty()) {
                notes.add(new String[]{st.selected, "name"});
            }
            for (List<Rich.Word> l : Rich.wrap(c, about, nw)) {
                notes.add(new String[]{join(l), "text"});
            }
            for (MapView.Mark k : ms) {
                for (List<Rich.Word> l : Rich.wrap(c, KnowledgeDate.shortLabel(k.date()) + ": " + k.headline(), nw)) {
                    notes.add(new String[]{join(l), "text"});
                }
            }
        }
        if (!st.message.isBlank()) {
            for (List<Rich.Word> l : Rich.wrap(c, st.message, nw)) {
                notes.add(new String[]{join(l), "gold"});
            }
        }
        int buttonsTop = side ? bottomY - (sel != null ? 20 : 0) : bottomY;
        for (String[] n : notes) {
            if (ly + Canvas.LINE_H > buttonsTop - 2) {
                break;
            }
            switch (n[1]) {
                case "days" -> {
                    int lx = nx0;
                    for (String idx : n[0].split("\\|")) {
                        int i = Integer.parseInt(idx);
                        String d = KnowledgeDate.shortLabel(days.get(i));
                        c.fill(lx, ly + 1, lx + 6, ly + 7, DAY_COLOURS[i % DAY_COLOURS.length]);
                        c.text(d, lx + 9, ly, TradePainter.TEXT, false);
                        lx += 16 + c.width(d);
                    }
                }
                case "name" -> c.bold(c.fit(n[0], nw), nx0, ly, TradePainter.NAME);
                case "gold" -> c.text(c.fit(n[0], nw), nx0, ly, TradePainter.GOLD, false);
                case "text" -> c.text(c.fit(n[0], nw), nx0, ly, TradePainter.TEXT, false);
                default -> c.text(c.fit(n[0], nw), nx0, ly, TradePainter.GRAY, false);
            }
            ly += Canvas.LINE_H;
        }
        String bye = "Close";
        int byeW = c.width(bye) + 14;
        if (!st.selected.isBlank() && sel != null) {
            String label = xaero ? "Add to Xaero's map" : "Show its place in chat";
            int bw = side ? nw : c.width(label) + 14;
            int bxx = nx0, byy = side ? bottomY - 20 : bottomY;
            TradePainter.button(c, bxx, byy, bw, 16, label, true, TradePainter.inside(mx, my, bxx, byy, bw, 16));
            hots.add(new TradePainter.Hot(bxx, byy, bxx + bw, byy + 16, "xaero", st.selected));
        }
        int cxx = side ? nx0 + nw - byeW : x1 - 6 - byeW;
        TradePainter.button(c, cxx, bottomY, byeW, 16, bye, true, TradePainter.inside(mx, my, cxx, bottomY, byeW, 16));
        hots.add(new TradePainter.Hot(cxx, bottomY, cxx + byeW, bottomY + 16, "close", ""));
        return hots;
    }

    /** Which labels go first: the town picked, towns in the news, big towns. */
    private static int rank(MapView v, State st, MapView.Town t) {
        return (t.name().equalsIgnoreCase(st.selected) ? 4 : 0) + (v.marksFor(t.name()).isEmpty() ? 0 : 2)
                + (t.major() ? 1 : 0);
    }

    private static String join(List<Rich.Word> line) {
        StringBuilder b = new StringBuilder();
        for (Rich.Word w : line) {
            b.append(b.length() == 0 || w.glue() ? "" : " ").append(w.text());
        }
        return b.toString();
    }

    /** The position of a town or a marked place, or null. */
    public static double[] position(MapView v, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (MapView.Town t : v.towns) {
            if (t.name().equalsIgnoreCase(name)) {
                return new double[]{t.lat(), t.lon()};
            }
        }
        for (MapView.Mark k : v.marks) {
            if (k.name().equalsIgnoreCase(name)) {
                return new double[]{k.lat(), k.lon()};
            }
        }
        return null;
    }

    private static void drawMarks(Canvas c, List<MapView.Mark> marks, List<Integer> days, int px, int py) {
        List<Integer> mine = new ArrayList<>();
        for (MapView.Mark k : marks) {
            if (!mine.contains(k.date())) {
                mine.add(k.date());
            }
        }
        for (int i = 0; i < mine.size(); i++) {
            int col = DAY_COLOURS[Math.max(0, days.indexOf(mine.get(i))) % DAY_COLOURS.length];
            int r = 4 + i * 2;
            c.fill(px - r, py - r, px + r + 1, py - r + 1, col);
            c.fill(px - r, py + r, px + r + 1, py + r + 1, col);
            c.fill(px - r, py - r, px - r + 1, py + r + 1, col);
            c.fill(px + r, py - r, px + r + 1, py + r + 1, col);
        }
    }

    /** A label's place beside a dot: right, left, above or below, not over another label; null when none is free. */
    private static int[] place(List<int[]> taken, Box box, int px, int py, int lw) {
        int[][] tries = {{px + 5, py - 4}, {px - 5 - lw, py - 4}, {px - lw / 2, py - 13}, {px - lw / 2, py + 5}};
        for (int[] t : tries) {
            int[] r = {t[0], t[1], t[0] + lw, t[1] + 8};
            if (r[0] < box.x0 + 1 || r[2] > box.x0 + box.w - 1 || r[1] < box.y0 + 1 || r[3] > box.y0 + box.h - 1) {
                continue;
            }
            boolean free = true;
            for (int[] o : taken) {
                if (r[0] < o[2] + 2 && r[2] + 2 > o[0] && r[1] < o[3] + 1 && r[3] + 1 > o[1]) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return t;
            }
        }
        return null;
    }

    private static void dashed(Canvas c, Box box, int xa, int ya, int xb, int yb, int col, int on, int off) {
        double dx = xb - xa, dy = yb - ya;
        double len = Math.hypot(dx, dy);
        int n = (int) Math.ceil(len);
        for (int i = 0; i <= n; i++) {
            if (i % (on + off) >= on) {
                continue;
            }
            double t = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(xa + dx * t), y = (int) Math.round(ya + dy * t);
            if (box.inside(x, y)) {
                c.fill(x, y, x + 1, y + 1, col);
            }
        }
    }

    private static void circle(Canvas c, Box box, int cx, int cy, int r, int col) {
        int n = (int) Math.ceil(2 * Math.PI * r);
        for (int i = 0; i < n; i++) {
            if (i % 4 >= 2) {
                continue;
            }
            double a = 2 * Math.PI * i / n;
            int x = (int) Math.round(cx + r * Math.cos(a)), y = (int) Math.round(cy + r * Math.sin(a));
            if (box.inside(x, y)) {
                c.fill(x, y, x + 1, y + 1, col);
            }
        }
    }

    /** The map's area on the screen. */
    record Box(MapView v, int x0, int y0, int w, int h) {
        int x(double lon) {
            return x0 + (int) Math.round((lon - v.minLon) / Math.max(1e-9, v.maxLon - v.minLon) * w);
        }

        int y(double lat) {
            return y0 + (int) Math.round((v.maxLat - lat) / Math.max(1e-9, v.maxLat - v.minLat) * h);
        }

        boolean inside(int x, int y) {
            return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
        }
    }
}
