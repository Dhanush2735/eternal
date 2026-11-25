package com.eternalengine.client.gui.paint;

import com.eternalengine.knowledge.BookView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 20: the history book from the approved preview 2: a leather book with two pages. The left page finds and lists
 * every name the player has heard, in groups, with how many notes; the right page has the tabs Notes, Journal and
 * Family tree: the open name's notes (the day, who said it, what they said), "Track on the compass", the linked names,
 * and what comes later only with the time traveller setting. Pure Java: the game draws it with Minecraft's font, the
 * layout probe into a picture.
 */
public final class BookPainter {

    static final int LEATHER = 0xFF6B4A2A;
    static final int LEATHER_DARK = 0xFF2B1D10;
    static final int LEATHER_LIGHT = 0xFF8A6239;
    static final int PAGE = 0xFFF3EAD2;
    static final int PAGE_EDGE = 0xFFC9B88F;
    static final int INK = 0xFF3B2F1E;
    static final int DARK = 0xFF2B1D10;
    static final int SOFT = 0xFF7A6A4A;
    static final int LINK = 0xFF1F4FB8;
    static final int RULE = 0xFFD3C39B;
    static final int CREAM = 0xFFF3EAD2;
    static final int ON = 0xFFE3D2A6;
    static final int HOVER = 0xFFEFE3C4;
    static final int BOX = 0xFFEFE3C4;
    static final int BOX_EDGE = 0xFFB39B6B;
    static final int TRACK = 0xFF4E6B3A;
    static final int LATER = 0xFF7D3C98;

    /** What the screen remembers between frames: scrolling, the search words, and where the pages split. */
    public static final class State {
        public int leftScroll;
        public int rightScroll;
        public int maxLeft;
        public int maxRight;
        public String filter = "";
        public boolean focused = true;
        /** The x where the left page ends (the mouse wheel scrolls the page it is over). */
        public int splitX;
    }

    private BookPainter() {
    }

    /** Clip-aware drawing inside one page. */
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

        void text(String s, int x, int y, int argb) {
            if (shows(y, Canvas.LINE_H - 1)) {
                c.text(s, x, y, argb, false);
            }
        }

        void bold(String s, int x, int y, int argb) {
            if (shows(y, Canvas.LINE_H - 1)) {
                c.bold(s, x, y, argb);
            }
        }
    }

    public static List<TradePainter.Hot> paint(Canvas c, BookView v, State st, int sw, int sh, int mx, int my) {
        List<TradePainter.Hot> hots = new ArrayList<>();
        int w = Math.min(560, sw - 8);
        int h = sh - 8;
        int x0 = (sw - w) / 2, x1 = x0 + w;
        int y0 = 4, y1 = y0 + h;

        // The leather cover and the title strip.
        c.fill(x0, y0, x1, y1, LEATHER);
        c.outline(x0, y0, x1, y1, LEATHER_DARK);
        c.outline(x0 + 2, y0 + 2, x1 - 2, y1 - 2, LEATHER_LIGHT);
        String close = "Close ×";
        int cw = c.width(close);
        c.text(close, x1 - 6 - cw, y0 + 5, TradePainter.GOLD, false);
        hots.add(new TradePainter.Hot(x1 - 8 - cw, y0 + 3, x1 - 3, y0 + 15, "close", ""));
        String title = "History book";
        c.bold(title, x0 + 7, y0 + 5, CREAM);
        int tx = x0 + 7 + c.width(title) + 7;
        c.text(c.fit(v.subtitle, Math.max(0, x1 - 12 - cw - tx)), tx, y0 + 5, 0xFFD9CBA6, false);

        int footer = 12;
        int py0 = y0 + 17, py1 = y1 - 4 - footer;
        int inner = w - 14;
        int lw = Math.max(110, Math.min(220, (int) (inner * 0.40d)));
        int lx0 = x0 + 5, lx1 = lx0 + lw;
        int rx0 = lx1 + 4, rx1 = x1 - 5;
        st.splitX = lx1 + 2;
        leftPage(c, v, st, hots, lx0, py0, lx1, py1, mx, my);
        rightPage(c, v, st, hots, rx0, py0, rx1, py1, mx, my);

        // The footer: what the last action did, or what the compass points to.
        String foot = !v.hint.isBlank() ? v.hint : v.trackingLabel;
        if (!foot.isBlank()) {
            c.text(c.fit(foot, w - 14), x0 + 7, y1 - 3 - footer + 2, 0xFFE8DCB8, false);
        }
        return hots;
    }

    // ------------------------------------------------------------------------------------------- the left page

    private static void leftPage(Canvas c, BookView v, State st, List<TradePainter.Hot> hots, int x0, int y0, int x1, int y1,
                                 int mx, int my) {
        c.fill(x0, y0, x1, y1, PAGE);
        c.outline(x0, y0, x1, y1, PAGE_EDGE);
        // The search field (typing goes here: the book has no other place to type).
        int sx0 = x0 + 4, sx1 = x1 - 4, sy0 = y0 + 4, sy1 = sy0 + 13;
        c.fill(sx0, sy0, sx1, sy1, 0xFFFFFAF0);
        c.outline(sx0, sy0, sx1, sy1, BOX_EDGE);
        String shown = st.filter.isEmpty() ? "Find a name in your book…" : st.filter + (st.focused ? "_" : "");
        c.text(c.fit(shown, sx1 - sx0 - 6), sx0 + 3, sy0 + 3, st.filter.isEmpty() ? SOFT : DARK, false);
        hots.add(new TradePainter.Hot(sx0, sy0, sx1, sy1, "search", ""));

        int listTop = sy1 + 4, listBottom = y1 - 14;
        Clip clip = new Clip(c, listTop, listBottom);
        String f = st.filter.toLowerCase(Locale.ROOT).trim();
        int y = listTop - st.leftScroll;
        String group = null;
        int shownCount = 0;
        // A narrow page counts in short ("5"; a name only heard is grey with no count), so the names keep their room.
        boolean wide = x1 - x0 >= 200;
        for (BookView.Entry e : v.entries) {
            if (!f.isEmpty() && !e.title().toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            String g = BookView.groupName(e.kind());
            if (!g.equals(group)) {
                group = g;
                y += shownCount == 0 ? 0 : 3;
                clip.text(g.toUpperCase(Locale.ROOT), x0 + 5, y, LEATHER);
                y += 11;
            }
            shownCount++;
            int rowTop = y - 1, rowBottom = y + 10;
            boolean on = e.id().equals(v.card);
            boolean over = mx >= x0 && mx < x1 && my >= rowTop && my < rowBottom && my >= listTop && my < listBottom;
            if (on || over) {
                clip.fill(x0 + 2, rowTop, x1 - 2, rowBottom, on ? ON : HOVER);
            }
            String count = e.notes() > 0 ? (wide ? e.notes() + (e.notes() == 1 ? " note" : " notes") : String.valueOf(e.notes()))
                    : wide ? "only the name" : "";
            int countW = c.width(count);
            clip.text(c.fit(e.title(), x1 - x0 - 12 - countW - (count.isEmpty() ? 0 : 6)), x0 + 5, y,
                    e.notes() > 0 ? DARK : SOFT);
            clip.text(count, x1 - 5 - countW, y, SOFT);
            if (clip.shows(rowTop, rowBottom - rowTop)) {
                hots.add(new TradePainter.Hot(x0 + 2, rowTop, x1 - 2, rowBottom, "card", e.id()));
            }
            clip.fill(x0 + 4, rowBottom, x1 - 4, rowBottom + 1, RULE);
            y += 12;
        }
        if (shownCount == 0) {
            String none = v.entries.isEmpty() ? "Your book is empty. Talk to people, or read a paper: what you hear goes"
                    + " in here." : "Nothing like that in your book.";
            y = wrapped(clip, c, none, x0 + 5, y + 2, x1 - x0 - 10, SOFT);
        }
        int content = y + st.leftScroll - listTop;
        st.maxLeft = Math.max(0, content - (listBottom - listTop));
        st.leftScroll = Math.max(0, Math.min(st.maxLeft, st.leftScroll));
        String note = v.heard + (v.heard == 1 ? " name" : " names") + " heard so far" + (wide ? "" : "; grey: only the name");
        c.text(c.fit(note, x1 - x0 - 10), x0 + 5, y1 - 12, SOFT, false);
    }

    // ------------------------------------------------------------------------------------------ the right page

    private static void rightPage(Canvas c, BookView v, State st, List<TradePainter.Hot> hots, int x0, int y0, int x1,
                                  int y1, int mx, int my) {
        c.fill(x0, y0, x1, y1, PAGE);
        c.outline(x0, y0, x1, y1, PAGE_EDGE);
        // Tabs.
        int tx = x0 + 5, ty0 = y0 + 4, ty1 = ty0 + 13;
        String[][] tabs = v.familyTree ? new String[][] {{"notes", "Notes"}, {"journal", "Journal"}, {"tree", "Family tree"}}
                : new String[][] {{"notes", "Notes"}, {"journal", "Journal"}};
        for (String[] t : tabs) {
            int tw = c.width(t[1]) + 10;
            boolean on = t[0].equals(v.tab);
            c.fill(tx, ty0, tx + tw, ty1, on ? INK : 0xFFE3D6B5);
            c.outline(tx, ty0, tx + tw, ty1, BOX_EDGE);
            c.text(t[1], tx + 5, ty0 + 3, on ? CREAM : INK, false);
            hots.add(new TradePainter.Hot(tx, ty0, tx + tw, ty1, "tab", t[0]));
            tx += tw + 4;
        }
        int top = ty1 + 5, bottom = y1 - 4;
        Clip clip = new Clip(c, top, bottom);
        int cx0 = x0 + 6, cwid = x1 - x0 - 12;
        int y = top - st.rightScroll;
        switch (v.tab) {
            case "journal" -> y = journal(c, clip, v, hots, cx0, y, cwid);
            case "tree" -> y = tree(c, clip, v, hots, cx0, y, cwid, mx, my);
            default -> y = notes(c, clip, v, hots, cx0, y, cwid, mx, my);
        }
        int content = y + st.rightScroll - top;
        st.maxRight = Math.max(0, content - (bottom - top));
        st.rightScroll = Math.max(0, Math.min(st.maxRight, st.rightScroll));
        if (st.maxRight > 0) {
            // A thin scroll bar on the page's right edge.
            int barH = Math.max(12, (bottom - top) * (bottom - top) / Math.max(1, content));
            int barY = top + (bottom - top - barH) * st.rightScroll / Math.max(1, st.maxRight);
            c.fill(x1 - 3, barY, x1 - 1, barY + barH, BOX_EDGE);
        }
    }

    private static int notes(Canvas c, Clip clip, BookView v, List<TradePainter.Hot> hots, int x, int y, int w, int mx,
                             int my) {
        if (v.card.isBlank()) {
            return box(clip, c, v.entries.isEmpty() ? "Your book is empty. When people tell you something, or you read a"
                    + " paper, the names and what was said go in here." : "Pick a name on the left.", x, y, w);
        }
        // The kind, as a small dark tag, and the title.
        String kind = v.kind.toUpperCase(Locale.ROOT);
        int kw = c.width(kind) + 8;
        clip.fill(x, y, x + kw, y + 10, INK);
        clip.text(kind, x + 4, y + 1, CREAM);
        y += 13;
        for (List<Rich.Word> line : Rich.wrap(c, v.title, w - 2)) {
            StringBuilder b = new StringBuilder();
            for (Rich.Word word : line) {
                b.append(b.length() == 0 ? "" : " ").append(word.text());
            }
            clip.bold(b.toString(), x, y, DARK);
            y += Canvas.LINE_H + 1;
        }
        y += 3;
        for (BookView.Note n : v.notes) {
            int dw = c.width(n.date()) + 8;
            clip.fill(x, y, x + dw, y + 10, LEATHER);
            clip.text(n.date(), x + 4, y + 1, CREAM);
            clip.bold(c.fit(n.by(), w - dw - 6), x + dw + 4, y + 1, LEATHER);
            y += 12;
            String said = "“" + n.text() + "”";
            List<List<Rich.Word>> lines = Rich.wrap(c, said, w - 2);
            y = drawRich(clip, c, lines, x, y, v);
            y += 3;
            clip.fill(x, y, x + w, y + 1, RULE);
            y += 4;
        }
        if (!v.nothing.isBlank()) {
            y = box(clip, c, v.nothing, x, y, w) + 4;
        }
        if (v.canTrack) {
            String label = v.tracking ? "Tracking · Stop" : "Track on the compass";
            int bw = c.width(label) + 12;
            boolean over = mx >= x && mx < x + bw && my >= y && my < y + 14;
            clip.fill(x, y, x + bw, y + 14, over ? 0xFF638A4A : TRACK);
            clip.text(label, x + 6, y + 3, 0xFFFFFFFF);
            if (clip.shows(y, 14)) {
                hots.add(new TradePainter.Hot(x, y, x + bw, y + 14, v.tracking ? "untrack" : "track", v.card));
            }
            if (!v.where.isBlank()) {
                String wtxt = c.fit(v.where, w - bw - 8);
                if (c.width(v.where) <= w - bw - 8) {
                    clip.text(wtxt, x + bw + 6, y + 3, SOFT);
                    y += 18;
                } else {
                    y += 17;
                    y = wrapped(clip, c, v.where, x, y, w, SOFT) + 3;
                }
            } else {
                y += 18;
            }
        }
        if (!v.links.isEmpty() || v.unheardLinks > 0) {
            clip.text("LINKED NAMES", x, y, LEATHER);
            y += 11;
            int lx = x;
            for (int i = 0; i < v.links.size(); i++) {
                BookView.Link l = v.links.get(i);
                String label = l.label() + (i < v.links.size() - 1 || v.unheardLinks > 0 ? " ·" : "");
                int lw = c.width(label);
                if (lx > x && lx + lw > x + w) {
                    lx = x;
                    y += Canvas.LINE_H + 1;
                }
                String shown = c.fit(label, w);
                clip.text(shown, lx, y, LINK);
                if (clip.shows(y, 10)) {
                    hots.add(new TradePainter.Hot(lx, y - 1, lx + Math.min(lw, w), y + 10, "card", l.id()));
                }
                lx += lw + c.width(" ");
            }
            if (!v.links.isEmpty()) {
                y += Canvas.LINE_H + 1;
            }
            if (v.unheardLinks > 0) {
                String more = (v.links.isEmpty() ? "" : "and ") + v.unheardLinks
                        + (v.unheardLinks == 1 ? " name you have not heard yet" : " names you have not heard yet");
                y = wrapped(clip, c, more, x, y, w, SOFT);
            }
            y += 4;
        }
        if (!v.later.isEmpty()) {
            int start = y;
            y += 4;
            clip.text("TIME TRAVELLER VIEW", x + 5, y, LATER);
            y += 12;
            for (String l : v.later) {
                y = wrapped(clip, c, "Later: " + l, x + 5, y, w - 10, INK) + 2;
            }
            y += 2;
            clip.fill(x, start, x + w, start + 1, BOX_EDGE);
            clip.fill(x, y, x + w, y + 1, BOX_EDGE);
            clip.fill(x, start, x + 1, y, BOX_EDGE);
            clip.fill(x + w - 1, start, x + w, y, BOX_EDGE);
            y += 4;
        }
        return y;
    }

    private static int journal(Canvas c, Clip clip, BookView v, List<TradePainter.Hot> hots, int x, int y, int w) {
        if (v.journal.isEmpty()) {
            return box(clip, c, "Your journal is empty. What you do (people you ask, papers you read, what you buy and"
                    + " sell) is written here with the day.", x, y, w);
        }
        for (BookView.Day d : v.journal) {
            clip.text(d.date(), x, y, SOFT);
            y += 10;
            String text = d.text() + (d.card().isBlank() ? "" : " [open]");
            List<List<Rich.Word>> lines = Rich.wrap(c, text, w - 4);
            int lineY = y;
            for (List<Rich.Word> line : lines) {
                StringBuilder b = new StringBuilder();
                for (Rich.Word word : line) {
                    b.append(b.length() == 0 ? "" : " ").append(word.text());
                }
                String s = b.toString();
                boolean last = line == lines.get(lines.size() - 1);
                if (last && s.endsWith("[open]")) {
                    String before = s.substring(0, s.length() - "[open]".length());
                    clip.text(before, x + 4, lineY, INK);
                    int ox = x + 4 + c.width(before);
                    clip.text("[open]", ox, lineY, LINK);
                    if (clip.shows(lineY, 10)) {
                        hots.add(new TradePainter.Hot(ox, lineY - 1, ox + c.width("[open]"), lineY + 10, "card", d.card()));
                    }
                } else {
                    clip.text(s, x + 4, lineY, INK);
                }
                lineY += Canvas.LINE_H;
            }
            y = lineY + 2;
            clip.fill(x, y, x + w, y + 1, 0xFFC9B88F);
            y += 4;
        }
        return y;
    }

    private static int tree(Canvas c, Clip clip, BookView v, List<TradePainter.Hot> hots, int x, int y, int w, int mx,
                            int my) {
        if (!v.treeNote.isBlank()) {
            y = wrapped(clip, c, v.treeNote, x, y, w, 0xFF5A4A2E) + 6;
        }
        for (int i = 0; i < v.tree.size(); i++) {
            BookView.Node n = v.tree.get(i);
            if (i > 0) {
                y += 4; // no lines between them: the book does not know yet who is whose son
            }
            String label = c.fit(n.label() + (n.now() ? " (now)" : ""), w - 14);
            int bw = c.width(label) + 12;
            boolean known = !n.id().isBlank();
            boolean over = known && mx >= x && mx < x + bw && my >= y && my < y + 14;
            clip.fill(x, y, x + bw, y + 14, over ? 0xFFFFFFFF : 0xFFFFFAF0);
            if (clip.shows(y, 14)) {
                c.outline(x, y, x + bw, y + 14, n.now() ? LATER : BOX_EDGE);
            }
            clip.text(label, x + 6, y + 3, known ? DARK : SOFT);
            if (known && clip.shows(y, 14)) {
                hots.add(new TradePainter.Hot(x, y, x + bw, y + 14, "card", n.id()));
            }
            y += 14;
        }
        return y + 4;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Note text with names: a name the player has heard is drawn as a link colour. */
    private static int drawRich(Clip clip, Canvas c, List<List<Rich.Word>> lines, int x, int y, BookView v) {
        int space = c.width(" ");
        for (List<Rich.Word> line : lines) {
            if (clip.shows(y, Canvas.LINE_H - 1)) {
                int cx = x;
                boolean first = true;
                for (Rich.Word word : line) {
                    if (!first && word.glue()) {
                        cx -= space;
                    }
                    first = false;
                    c.text(word.text(), cx, y, word.name() ? LINK : INK, false);
                    cx += c.width(word.text()) + space;
                }
            }
            y += Canvas.LINE_H;
        }
        return y;
    }

    private static int wrapped(Clip clip, Canvas c, String s, int x, int y, int w, int color) {
        for (List<Rich.Word> line : Rich.wrap(c, s, w)) {
            StringBuilder b = new StringBuilder();
            for (Rich.Word word : line) {
                b.append(b.length() == 0 || word.glue() ? "" : " ").append(word.text());
            }
            clip.text(b.toString(), x, y, color);
            y += Canvas.LINE_H;
        }
        return y;
    }

    /** A light box with a darker left edge (the preview's "nothing" box). */
    private static int box(Clip clip, Canvas c, String s, int x, int y, int w) {
        List<List<Rich.Word>> lines = Rich.wrap(c, s, w - 12);
        int h = lines.size() * Canvas.LINE_H + 8;
        clip.fill(x, y, x + w, y + h, BOX);
        clip.fill(x, y, x + 2, y + h, BOX_EDGE);
        int ty = y + 4;
        for (List<Rich.Word> line : lines) {
            StringBuilder b = new StringBuilder();
            for (Rich.Word word : line) {
                b.append(b.length() == 0 || word.glue() ? "" : " ").append(word.text());
            }
            clip.text(b.toString(), x + 7, ty, INK);
            ty += Canvas.LINE_H;
        }
        return y + h;
    }
}
