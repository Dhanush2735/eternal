package com.eternalengine.client.gui.paint;

import com.eternalengine.news.PaperView;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 19d: the newspaper screen from the approved preview: the masthead, the date line with the price, the lead story,
 * the other stories in columns, today's prices, a small advertisement, and the note on what reading it did. A
 * handwritten newsletter is drawn on older paper in brown ink. The mouse wheel scrolls a long paper.
 */
public final class PaperPainter {

    /** What the screen remembers between frames. */
    public static final class State {
        public int scroll;
        public int maxScroll;
    }

    private PaperPainter() {
    }

    /** Clip-aware drawing on the paper: only what lies inside the paper is drawn. */
    private record Clip(Canvas c, int top, int bottom) {
        boolean shows(int y, int h) {
            return y >= top && y + h <= bottom;
        }

        void fill(int x0, int y0, int x1, int y1, int argb) {
            int a = Math.max(y0, top), b = Math.min(y1, bottom);
            if (b > a) {
                c.fill(x0, a, x1, b, argb);
            }
        }
    }

    public static List<TradePainter.Hot> paint(Canvas c, PaperView v, State st, int sw, int sh, int mx, int my) {
        List<TradePainter.Hot> hots = new ArrayList<>();
        boolean hand = v.handwritten;
        int paper = hand ? 0xFFE8D9B0 : 0xFFF3ECD8;
        int ink = hand ? 0xFF3B2A14 : 0xFF1E1E1E;
        int soft = hand ? 0xFF6B5636 : 0xFF5A5A5A;
        int names = hand ? 0xFF7A1F1F : 0xFF1F3A93;
        int w = Math.min(420, sw - 12);
        int x0 = (sw - w) / 2, x1 = x0 + w;
        int y0 = 6, y1 = sh - 6;

        // The strip above the paper.
        String top = v.name + " · your copy of " + com.eternalengine.knowledge.KnowledgeDate.label(v.date);
        c.fill(x0, y0, x1, y0 + 13, 0xE0101014);
        c.text(c.fit(top, w - 40), x0 + 4, y0 + 3, 0xFFE0E0E0, false);
        String close = "Close ×";
        int cw = c.width(close);
        c.text(close, x1 - 4 - cw, y0 + 3, TradePainter.GOLD, false);
        hots.add(new TradePainter.Hot(x1 - 6 - cw, y0, x1, y0 + 13, "close", ""));

        int py0 = y0 + 14, py1 = y1;
        c.fill(x0, py0, x1, py1, paper);
        c.outline(x0, py0, x1, py1, 0xFF000000);
        Clip clip = new Clip(c, py0 + 2, py1 - 2);
        int m = 10;
        int cx0 = x0 + m, cx1 = x1 - m, cwid = cx1 - cx0;
        int y = py0 + 8 - st.scroll;

        // Masthead.
        float scale = 2.0f;
        while (scale > 1.0f && c.width(v.name) * scale > cwid) {
            scale -= 0.25f;
        }
        int mw = (int) (c.width(v.name) * scale);
        if (clip.shows(y, (int) (9 * scale))) {
            c.textScaled(v.name, x0 + (w - mw) / 2, y, ink, scale);
        }
        y += (int) (9 * scale) + 5;
        clip.fill(cx0, y, cx1, y + 1, soft);
        y += 3;
        String line = c.fit(v.line, cwid);
        if (clip.shows(y, 9)) {
            c.text(line, x0 + (w - c.width(line)) / 2, y, ink, false);
        }
        y += 10;
        clip.fill(cx0, y, cx1, y + 1, soft);
        clip.fill(cx0, y + 2, cx1, y + 3, soft);
        y += 8;

        // Step 19f: the new history.
        if (!v.fork.isBlank()) {
            List<List<Rich.Word>> f = Rich.wrap(c, v.fork, cwid - 8);
            int fh = f.size() * Canvas.LINE_H + 6;
            clip.fill(cx0, y, cx1, y + fh, 0x30B03030);
            clip.fill(cx0, y, cx0 + 2, y + fh, 0xFFB03030);
            Rich.draw(c, f, cx0 + 6, y + 3, 0xFF8B1A1A, 0xFF8B1A1A, clip.top, clip.bottom, false);
            y += fh + 6;
        }

        // The lead story.
        if (!v.stories.isEmpty()) {
            PaperView.Story lead = v.stories.get(0);
            y = Rich.draw(c, Rich.wrap(c, lead.headline(), cwid), cx0, y, ink, ink, clip.top, clip.bottom, true);
            y += 2;
            y = Rich.draw(c, Rich.wrap(c, lead.text(), cwid), cx0, y, ink, names, clip.top, clip.bottom, false);
            y += 6;
            clip.fill(cx0, y, cx1, y + 1, soft);
            y += 6;
        }

        // The other stories, in two columns when there is room.
        int cols = cwid >= 280 ? 2 : 1;
        int gap = 12;
        int colW = (cwid - gap * (cols - 1)) / cols;
        int[] colY = new int[cols];
        for (int k = 0; k < cols; k++) {
            colY[k] = y;
        }
        for (int i = 1; i < v.stories.size(); i++) {
            PaperView.Story s = v.stories.get(i);
            int k = 0;
            for (int j = 1; j < cols; j++) {
                if (colY[j] < colY[k]) {
                    k = j;
                }
            }
            int sx = cx0 + k * (colW + gap);
            int sy = Rich.draw(c, Rich.wrap(c, s.headline(), colW), sx, colY[k], ink, ink, clip.top, clip.bottom, true);
            sy = Rich.draw(c, Rich.wrap(c, s.text(), colW), sx, sy + 1, ink, names, clip.top, clip.bottom, false);
            colY[k] = sy + 8;
        }
        if (cols == 2) {
            int colTop = y, colBottom = Math.max(colY[0], colY[1]) - 8;
            if (colBottom > colTop) {
                clip.fill(cx0 + colW + gap / 2, colTop, cx0 + colW + gap / 2 + 1, colBottom, 0x60000000);
            }
        }
        for (int k = 0; k < cols; k++) {
            y = Math.max(y, colY[k]);
        }

        // Prices.
        if (!v.prices.isEmpty()) {
            int bh = (v.prices.size() + 1) * Canvas.LINE_H + 8;
            clip.fill(cx0, y, cx1, y + 1, soft);
            clip.fill(cx0, y + bh, cx1, y + bh + 1, soft);
            if (clip.shows(y + 4, 9)) {
                c.bold(c.fit(v.pricesTitle.toUpperCase(java.util.Locale.ROOT), cwid), cx0 + 2, y + 4, ink);
            }
            int ly = y + 4 + Canvas.LINE_H;
            for (String p : v.prices) {
                if (clip.shows(ly, 9)) {
                    c.text(c.fit(p, cwid - 4), cx0 + 2, ly, ink, false);
                }
                ly += Canvas.LINE_H;
            }
            y += bh + 8;
        }

        // The advertisement.
        if (!v.ad.isBlank()) {
            List<List<Rich.Word>> ad = Rich.wrap(c, v.ad, Math.min(cwid, 260) - 12);
            int aw = Math.min(cwid, 260), ah = ad.size() * Canvas.LINE_H + 8;
            int ax = x0 + (w - aw) / 2;
            clip.fill(ax, y, ax + aw, y + 1, ink);
            clip.fill(ax, y + ah, ax + aw, y + ah + 1, ink);
            clip.fill(ax, y, ax + 1, y + ah + 1, ink);
            clip.fill(ax + aw - 1, y, ax + aw, y + ah + 1, ink);
            int ly = y + 4;
            for (List<Rich.Word> l : ad) {
                StringBuilder b = new StringBuilder();
                for (Rich.Word wd : l) {
                    b.append(b.length() == 0 ? "" : " ").append(wd.text());
                }
                if (clip.shows(ly, 9)) {
                    c.text(b.toString(), ax + (aw - c.width(b.toString())) / 2, ly, ink, false);
                }
                ly += Canvas.LINE_H;
            }
            y += ah + 10;
        }

        // What reading it did.
        if (!v.note.isBlank()) {
            y = Rich.draw(c, Rich.wrap(c, v.note, cwid), cx0, y, soft, soft, clip.top, clip.bottom, false);
            y += 6;
        }

        int contentH = y + st.scroll - (py0 + 8);
        st.maxScroll = Math.max(0, contentH - (py1 - py0 - 12));
        if (st.maxScroll > 0) {
            int track = py1 - py0 - 4;
            int barH = Math.max(10, track * (py1 - py0) / Math.max(1, contentH + 12));
            int barY = py0 + 2 + (track - barH) * st.scroll / Math.max(1, st.maxScroll);
            c.fill(x1 - 4, barY, x1 - 2, barY + barH, 0x80000000);
        }
        return hots;
    }
}
