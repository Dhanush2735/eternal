package com.eternalengine.client.gui;

import com.eternalengine.knowledge.TalkView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;

/**
 * Where everything in the talk box goes, on a screen of a given size (step 18c). Plain Java with no Minecraft
 * classes, so a probe can lay the box out for every screen size and check that nothing leaves the screen.
 *
 * <p>Why it exists: in the user's game (1920 x 1080 with the automatic GUI scale, which is 480 x 270 GUI units)
 * the step-18 box began at 30% of the width. That left its body 258 units, but the row "Back, Other topics, the
 * name box, Ask, Goodbye" needs about 282, so Goodbye went off the right edge of the screen; and the date did not
 * fit beside the name, so it was left out.
 *
 * <p>Now the box keeps the approved look (from 30% of the width to near the right edge) when that leaves room, and
 * otherwise grows to the left. The date goes on a line of its own when it does not fit beside the name (it is never
 * left out). The buttons wrap onto a second row. Labels that are too long end in "…". When the screen is too low
 * the box covers the hotbar, then packs its rows tighter, and at last shows only some lines of what the villager
 * says, which the mouse wheel scrolls.
 */
public final class TalkLayout {

    public static final int LINE_H = 10;
    /** Room the body needs for the five buttons in one row and comfortable lines. */
    static final int MIN_BODY_W = 300;
    /** Lines longer than this are hard to read: on wide screens the box stops growing. */
    static final int MAX_BOX_W = 520;
    static final int PORTRAIT_W = 46;
    static final int PORTRAIT_H = 75;
    static final int PAD = 6;
    /** Space kept free above the box. */
    static final int TOP = 4;
    static final String BACK = "◀ Back";
    static final String TOPICS = "Other topics";
    static final String ASK = "Ask";
    static final String BYE = "Goodbye";
    static final String ASK_ABOUT = "Ask about:";
    static final String HINT_MARK = "⌖ ";
    static final String ELLIPSIS = "…";

    public enum Kind { OPTION, CHIP, BACK, TOPICS, ASK, BYE, CLOSE }

    /** A piece of plain text at a place. */
    public record Text(String text, int x, int y, int w) {
    }

    /** A word of what the villager says; {@code cardId} is set for gold names. */
    public record Word(String text, int x, int y, int w, String cardId) {
    }

    /** Something to click. {@code index} is the option or name number for OPTION and CHIP. */
    public record Box(Kind kind, int index, int x, int y, int w, int h, String number, String label) {
        public boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // ------------------------------------------------------------------ the result

    public int width, height;
    public int x0, y0, x1, y1;
    public int bodyX, bodyW;
    public boolean portrait;
    public int portraitX, portraitY;
    public Text name, job, date;
    public final List<Word> words = new ArrayList<>();
    public final List<Text> hint = new ArrayList<>();
    /** The "Ask about:" label in front of the names, or null when there are no names. */
    public Text askAbout;
    public final List<Box> boxes = new ArrayList<>();
    public int askX, askY, askW, askH;
    /** The grey hint inside the name box, as long as fits (Minecraft does not shorten it); and whether the box shows. */
    public String askHint = "";
    public boolean askVisible = true;
    /** Step 21: the talk is over (the person has gone): only Goodbye is offered, the name box is hidden. */
    public boolean ended;
    /** The thin line above the buttons. */
    public int ruleY;
    /** What the villager says: top of the first line shown, all lines, lines shown, first line shown. */
    public int textTop, textLines, textShown, scroll;
    /** Width the villager's words wrap to (8 less than the body when they scroll, for the arrows). */
    public int textW;
    public boolean overHotbar, compact;
    /**
     * Scrolling with the mouse wheel: {@code scroll} goes from 0 to {@code scrollMax}. Normally the villager's words
     * scroll (a line a step); in the last resort ({@code wholeBox}) everything in the box does (10 units a step).
     */
    public int scrollMax;
    public boolean wholeBox;
    /** The scroll arrows: where, and whether there is more above or below. */
    public int arrowX, arrowUpY, arrowDownY;
    public boolean moreAbove, moreBelow;
    /** False only when even the smallest form does not fit (a window smaller than Minecraft allows). */
    public boolean fits;

    private ToIntFunction<String> font;

    private TalkLayout() {
    }

    /**
     * Lays the box out. {@code font} measures text in GUI units (Minecraft's {@code Font::width}); {@code scroll} is
     * the first line of the villager's words to show when they do not all fit (clamped here).
     */
    public static TalkLayout of(int width, int height, ToIntFunction<String> font, TalkView view, int scroll) {
        TalkLayout last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            TalkLayout l = new TalkLayout();
            l.place(width, height, font, view, attempt >= 1, attempt >= 2, Integer.MAX_VALUE, 0);
            if (l.fits) {
                return l;
            }
            last = l;
        }
        // Still too tall: show only the lines of the villager's words that fit (at least 2); the wheel scrolls them.
        int lines = last.textShown + Math.floorDiv(last.y0 - TOP, LINE_H);
        if (lines >= 2) {
            TalkLayout l = new TalkLayout();
            l.place(width, height, font, view, true, true, lines, scroll);
            if (l.fits) {
                return l;
            }
        }
        // Last resort (very long lists on the smallest screens): everything in the box scrolls with the wheel, so
        // every question, name and button can still be reached.
        TalkLayout l = new TalkLayout();
        l.wholeBox = true;
        l.place(width, height, font, view, true, true, Integer.MAX_VALUE, scroll);
        return l;
    }

    private int tw(String s) {
        return s == null || s.isEmpty() ? 0 : font.applyAsInt(s);
    }

    /** {@code s} shortened with "…" to at most {@code w} units; empty if not even "…" fits. */
    String fit(String s, int w) {
        if (s == null || s.isEmpty() || tw(s) <= w) {
            return s == null ? "" : s;
        }
        if (tw(ELLIPSIS) > w) {
            return "";
        }
        int end = s.length();
        while (end > 0) {
            end--;
            if (end > 0 && Character.isLowSurrogate(s.charAt(end))) {
                end--;
            }
            String cut = s.substring(0, end).stripTrailing() + ELLIPSIS;
            if (tw(cut) <= w) {
                return cut;
            }
        }
        return ELLIPSIS;
    }

    private void place(int width, int height, ToIntFunction<String> font, TalkView view, boolean overHotbar,
                       boolean compact, int maxTextLines, int wantScroll) {
        this.font = font;
        this.width = width;
        this.height = height;
        this.overHotbar = overHotbar;
        this.compact = compact;
        this.ended = view.ended;

        // -------- across: the approved place if it leaves room, else wider to the left
        int margin = Math.max(4, Math.min(12, width / 40));
        x1 = width - margin;
        x0 = width >= 420 ? (int) (width * 0.30d) : margin;
        int portraitSpace = PORTRAIT_W + 8;
        int need = PAD + portraitSpace + MIN_BODY_W + PAD;
        if (x1 - x0 < need) {
            x0 = Math.max(margin, x1 - need);
        }
        if (x1 - x0 > MAX_BOX_W) {
            x0 = x1 - MAX_BOX_W;
        }
        portrait = (x1 - x0) - 2 * PAD - portraitSpace >= 270;
        portraitX = x0 + PAD;
        bodyX = x0 + PAD + (portrait ? portraitSpace : 0);
        bodyW = Math.max(40, x1 - PAD - bodyX - (wholeBox ? 8 : 0));

        // -------- down, measured from 0 and moved into place at the end
        int y = 0;

        // Name and job on the first line, the close button at its right end, and the date beside them or below.
        int headRight = x1 - 22;
        int row1 = Math.max(0, headRight - bodyX);
        String nm = fit(view.npcName, row1);
        int jobRoom = row1 - tw(nm) - 6;
        String jb = jobRoom > tw(ELLIPSIS) ? fit(view.job, jobRoom) : "";
        name = new Text(nm, bodyX, y, tw(nm));
        job = new Text(jb, bodyX + tw(nm) + 6, y, tw(jb));
        String longDate = view.dateLabel == null ? "" : view.dateLabel;
        int afterJob = jb.isEmpty() ? tw(nm) : tw(nm) + 6 + tw(jb);
        int headH = 12;
        if (longDate.isEmpty()) {
            date = new Text("", bodyX, y, 0);
        } else if (afterJob + 10 + tw(longDate) <= row1) {
            date = new Text(longDate, headRight - tw(longDate), y, tw(longDate));
        } else {
            String d = longDate;
            if (tw(d) > bodyW) {
                int comma = d.indexOf(", ");
                d = comma > 0 ? d.substring(comma + 2) : d; // "14 September 1948" without the weekday
            }
            d = fit(d, bodyW);
            date = new Text(d, bodyX, y + LINE_H, tw(d));
            headH += LINE_H;
        }
        y += headH + 3;

        // What the villager says: word-wrapped, gold names keep their card id.
        textTop = y;
        boolean scrolling = maxTextLines != Integer.MAX_VALUE;
        textW = scrolling ? bodyW - 8 : bodyW; // room on the right for the scroll arrows
        List<List<Word>> lines = wrapWords(view.text, textW);
        textLines = Math.max(1, lines.size());
        textShown = Math.min(textLines, Math.max(1, maxTextLines));
        scroll = wholeBox ? 0 : Math.max(0, Math.min(wantScroll, textLines - textShown));
        scrollMax = textLines - textShown;
        for (int i = scroll; i < scroll + textShown && i < lines.size(); i++) {
            for (Word w : lines.get(i)) {
                words.add(new Word(w.text(), w.x(), textTop + (i - scroll) * LINE_H, w.w(), w.cardId()));
            }
        }
        y = textTop + textShown * LINE_H;

        // The way there ("⌖ Naldurg: about 240 km west of here").
        String hintText = view.hint == null ? "" : view.hint.strip();
        if (!hintText.isEmpty()) {
            List<String> hl = wrapPlain(HINT_MARK + hintText, bodyW);
            for (int i = 0; i < hl.size(); i++) {
                hint.add(new Text(hl.get(i), bodyX, y + 2 + i * LINE_H, tw(hl.get(i))));
            }
            y += 4 + hl.size() * LINE_H;
        }
        y += 4;

        // Numbered questions in two columns; a question too long for half the width gets the whole row (so it is
        // never cut short when it can be read whole), and the numbers stay in reading order.
        int optH = compact ? 13 : 16;
        int optGap = compact ? 2 : 3;
        int count = Math.min(9, view.options.size());
        int cols = (bodyW - 4) / 2 >= 100 ? 2 : 1;
        int colW = cols == 2 ? (bodyW - 4) / 2 : bodyW;
        int row = 0;
        boolean rightFree = false;
        for (int i = 0; i < count; i++) {
            String number = (i + 1) + ".";
            String full = view.options.get(i).label();
            boolean wide = cols == 1 || 5 + tw(number) + 4 + tw(full) + 4 > colW;
            if (wide && rightFree) {
                widenLast(bodyW); // the question alone on its row takes the whole row
                row++;
                rightFree = false;
            }
            int bx = rightFree ? bodyX + colW + 4 : bodyX;
            int w = wide ? bodyW : colW;
            String label = fit(full, w - 5 - tw(number) - 4 - 4);
            boxes.add(new Box(Kind.OPTION, i, bx, y + row * (optH + optGap), w, optH, number, label));
            if (wide || rightFree) {
                row++;
                rightFree = false;
            } else {
                rightFree = true;
            }
        }
        if (rightFree) {
            widenLast(bodyW); // so is the last one when it is alone
        }
        if (count > 0) {
            y += (row + (rightFree ? 1 : 0)) * (optH + optGap) + 2;
        }

        // "Ask about:" and the names heard so far.
        if (!view.names.isEmpty()) {
            int chipH = 12;
            int step = compact ? 13 : 14;
            int labelW = tw(ASK_ABOUT);
            int rowStart = bodyX + labelW + 5;
            int rowRight = bodyX + bodyW;
            int cy = y;
            if (rowRight - rowStart < 60) {
                rowStart = bodyX; // too narrow beside the label: the names start on the next line
                cy += step;
            }
            askAbout = new Text(ASK_ABOUT, bodyX, y + 2, labelW);
            int cx = rowStart;
            for (int j = 0; j < view.names.size(); j++) {
                String label = view.names.get(j).label();
                int w = tw(label) + 10;
                if (cx + w > rowRight && cx > rowStart) {
                    cx = rowStart;
                    cy += step;
                }
                if (cx + w > rowRight) {
                    label = fit(label, rowRight - cx - 10);
                    w = tw(label) + 10;
                }
                boxes.add(new Box(Kind.CHIP, j, cx, cy, w, chipH, "", label));
                cx += w + 4;
            }
            y = cy + chipH + 5;
        }

        // Back, Other topics, the box to type a name, Ask, Goodbye: one row if they fit, else two.
        int ctrlH = compact ? 14 : 16;
        int gap = 4;
        y += compact ? 3 : 5;
        ruleY = y - (compact ? 3 : 5);
        int wb = view.canBack ? tw(BACK) + 12 : 0;
        int wt = tw(TOPICS) + 12;
        int wa = tw(ASK) + 12;
        int wy = tw(BYE) + 12;
        int minAsk = 60;
        int fixed = (view.canBack ? wb + gap : 0) + wt + gap + gap + wa + gap + wy;
        int bottom;
        if (view.ended) {
            // Step 21: the talk is over: Goodbye only, at the right end of the row; no topics, no name box.
            boxes.add(new Box(Kind.BYE, -1, bodyX + bodyW - wy, y, wy, ctrlH, "", BYE));
            askX = bodyX;
            askY = y;
            askW = 0;
            askH = ctrlH;
            askVisible = false;
            bottom = y + ctrlH;
        } else if (bodyW - fixed >= minAsk) {
            int x = bodyX;
            if (view.canBack) {
                boxes.add(new Box(Kind.BACK, -1, x, y, wb, ctrlH, "", BACK));
                x += wb + gap;
            }
            boxes.add(new Box(Kind.TOPICS, -1, x, y, wt, ctrlH, "", TOPICS));
            x += wt + gap;
            askX = x;
            askY = y;
            askW = bodyW - fixed;
            askH = ctrlH;
            x += askW + gap;
            boxes.add(new Box(Kind.ASK, -1, x, y, wa, ctrlH, "", ASK));
            x += wa + gap;
            boxes.add(new Box(Kind.BYE, -1, x, y, wy, ctrlH, "", BYE));
            bottom = y + ctrlH;
        } else {
            int x = bodyX;
            if (view.canBack) {
                boxes.add(new Box(Kind.BACK, -1, x, y, wb, ctrlH, "", BACK));
                x += wb + gap;
            }
            boxes.add(new Box(Kind.TOPICS, -1, x, y, wt, ctrlH, "", TOPICS));
            x += wt + gap;
            boolean byeOnTop = x + wy <= bodyX + bodyW;
            if (byeOnTop) {
                boxes.add(new Box(Kind.BYE, -1, bodyX + bodyW - wy, y, wy, ctrlH, "", BYE));
            }
            int y2 = y + ctrlH + gap;
            askX = bodyX;
            askY = y2;
            askW = Math.max(20, bodyW - gap - wa);
            askH = ctrlH;
            boxes.add(new Box(Kind.ASK, -1, bodyX + askW + gap, y2, wa, ctrlH, "", ASK));
            bottom = y2 + ctrlH;
            if (!byeOnTop) {
                int y3 = bottom + gap;
                boxes.add(new Box(Kind.BYE, -1, bodyX, y3, wy, ctrlH, "", BYE));
                bottom = y3 + ctrlH;
            }
        }
        int contentH = bottom + 6;
        for (String h : new String[]{"Ask about a name…", "Type a name…", "Name…", ""}) {
            if (tw(h) <= askW - 8) {
                askHint = h;
                break;
            }
        }

        // -------- sit on the hotbar line (or over the hotbar), grow upward
        int boxH = Math.max(PAD + contentH, portrait ? PORTRAIT_H + 2 * PAD : 0);
        y1 = overHotbar ? height - 4 : height - 30;
        y0 = y1 - boxH;
        fits = y0 >= TOP;
        if (wholeBox) {
            placeScrolled(boxH, wantScroll);
            return;
        }
        int dy = y0 + PAD;
        shift(dy);
        portraitY = y0 + PAD;
        boxes.add(new Box(Kind.CLOSE, -1, x1 - 16, y0 + 4, 12, 11, "", "×"));
        arrowX = bodyX + textW + 2;
        arrowUpY = textTop + 1;
        arrowDownY = textTop + textShown * LINE_H - 5;
        moreAbove = scroll > 0;
        moreBelow = scroll + textShown < textLines;
    }

    /**
     * The last resort: the box fills the screen from the top to just above the bottom, and its contents move up by
     * {@code step} x 10 units. Only what is wholly inside the box (and clear of the close button) is shown and can be
     * clicked; the name box hides while it is scrolled out.
     */
    private void placeScrolled(int boxH, int step) {
        y0 = TOP;
        y1 = height - 4;
        int view = y1 - y0;
        int over = Math.max(0, boxH - view);
        scrollMax = (over + 9) / 10;
        scroll = Math.max(0, Math.min(step, scrollMax));
        int offset = Math.min(over, scroll * 10);
        shift(y0 + PAD - offset);
        portraitY = y0 + PAD;
        fits = true;
        int top = y0 + 2, bottom = y1 - 2;
        Box close = new Box(Kind.CLOSE, -1, x1 - 16, y0 + 4, 12, 11, "", "×");
        java.util.function.Predicate<int[]> shows = r -> r[1] >= top && r[1] + r[3] <= bottom
                && !(r[0] < close.x() + close.w() && close.x() < r[0] + r[2] && r[1] < close.y() + close.h() && close.y() < r[1] + r[3]);
        words.removeIf(w -> !shows.test(new int[]{w.x(), w.y(), w.w(), w.cardId() != null ? 10 : 9}));
        hint.removeIf(t -> !shows.test(new int[]{t.x(), t.y(), t.w(), 9}));
        boxes.removeIf(b -> !shows.test(new int[]{b.x(), b.y(), b.w(), b.h()}));
        if (askAbout != null && !shows.test(new int[]{askAbout.x(), askAbout.y(), askAbout.w(), 9})) {
            askAbout = null;
        }
        if (!shows.test(new int[]{name.x(), name.y(), Math.max(1, name.w()), 9})) {
            name = new Text("", name.x(), name.y(), 0);
            job = new Text("", job.x(), job.y(), 0);
        }
        if (!date.text().isEmpty() && !shows.test(new int[]{date.x(), date.y(), date.w(), 9})) {
            date = new Text("", date.x(), date.y(), 0);
        }
        askVisible = !ended && shows.test(new int[]{askX - 1, askY - 1, askW + 2, askH + 2});
        if (ruleY < top || ruleY > bottom) {
            ruleY = -1;
        }
        boxes.add(close);
        arrowX = bodyX + bodyW + 2;
        arrowUpY = close.y() + close.h() + 3; // below the close button
        arrowDownY = y1 - PAD - 3;
        moreAbove = offset > 0;
        moreBelow = offset < over;
    }

    /** Makes the last question box {@code w} wide (its label already fits the narrower box). */
    private void widenLast(int w) {
        Box b = boxes.remove(boxes.size() - 1);
        boxes.add(new Box(b.kind(), b.index(), b.x(), b.y(), w, b.h(), b.number(), b.label()));
    }

    private void shift(int dy) {
        name = new Text(name.text(), name.x(), name.y() + dy, name.w());
        job = new Text(job.text(), job.x(), job.y() + dy, job.w());
        date = new Text(date.text(), date.x(), date.y() + dy, date.w());
        List<Word> ws = new ArrayList<>(words);
        words.clear();
        for (Word w : ws) {
            words.add(new Word(w.text(), w.x(), w.y() + dy, w.w(), w.cardId()));
        }
        List<Text> hs = new ArrayList<>(hint);
        hint.clear();
        for (Text t : hs) {
            hint.add(new Text(t.text(), t.x(), t.y() + dy, t.w()));
        }
        if (askAbout != null) {
            askAbout = new Text(askAbout.text(), askAbout.x(), askAbout.y() + dy, askAbout.w());
        }
        List<Box> bs = new ArrayList<>(boxes);
        boxes.clear();
        for (Box b : bs) {
            boxes.add(new Box(b.kind(), b.index(), b.x(), b.y() + dy, b.w(), b.h(), b.number(), b.label()));
        }
        askY += dy;
        ruleY += dy;
        textTop += dy;
    }

    // ------------------------------------------------------------------ wrapping

    /** Word-wraps what the villager says into lines (x set, y = 0); gold names keep their card id. */
    private List<List<Word>> wrapWords(String text, int maxW) {
        List<String[]> tokens = new ArrayList<>();
        String src = text == null ? "" : text;
        Matcher m = TalkView.LINK.matcher(src);
        int last = 0;
        boolean prevSpace = true;
        while (m.find()) {
            prevSpace = addTokens(tokens, src.substring(last, m.start()), null, prevSpace);
            prevSpace = addTokens(tokens, m.group(2), m.group(1), prevSpace);
            last = m.end();
        }
        addTokens(tokens, src.substring(last), null, prevSpace);
        int space = tw(" ");
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        int x = 0;
        for (String[] t : tokens) {
            if (t[0].equals("\n")) {
                lines.add(line);
                line = new ArrayList<>();
                x = 0;
                continue;
            }
            String s = t[0];
            int w = tw(s);
            if (w > maxW) {
                s = fit(s, maxW); // one word wider than the box (never in the packs): shortened, not cut off
                w = tw(s);
            }
            boolean glue = t[2] != null; // no space before punctuation that follows a gold name
            if (x > 0 && x + (glue ? 0 : space) + w > maxW) {
                lines.add(line);
                line = new ArrayList<>();
                x = 0;
            } else if (x > 0 && !glue) {
                x += space;
            }
            line.add(new Word(s, bodyX + x, 0, w, t[1]));
            x += w;
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * Splits a piece of text into words. The first word sticks to the word before it when there was no space
     * between them ("Naldurg," right after the gold name). Returns true if the piece ends in a space.
     */
    static boolean addTokens(List<String[]> out, String s, String cardId, boolean prevSpace) {
        if (s == null || s.isEmpty()) {
            return prevSpace;
        }
        boolean leadingSpace = Character.isWhitespace(s.charAt(0));
        String[] parts = s.trim().split("\\s+");
        boolean first = true;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            boolean glue = first && !leadingSpace && !prevSpace && !out.isEmpty();
            out.add(new String[]{part, cardId, glue ? "glue" : null});
            first = false;
        }
        return Character.isWhitespace(s.charAt(s.length() - 1));
    }

    /** Plain word wrap to {@code w} units. */
    private List<String> wrapPlain(String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String next = line.length() == 0 ? word : line + " " + word;
            if (tw(next) <= w || line.length() == 0) {
                line.setLength(0);
                line.append(tw(next) <= w ? next : fit(next, w));
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(tw(word) <= w ? word : fit(word, w));
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
