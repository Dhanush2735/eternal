package com.eternalengine.client.gui;

import com.eternalengine.knowledge.TalkView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;

/**
 * Where everything in the talk box from the approved interaction sheet goes, on a screen of a given size.
 * Plain Java with no Minecraft classes, so a probe can lay the box out for every screen size and check
 * that nothing leaves the screen.
 *
 * <p>The sheet's box, top to bottom: the portrait with the name (and the job's sign in the job line),
 * the date and the close; the one big action button for the person's trade; the roles band (their
 * standing in the street); what they say, with gold clickable names; the way there; the names heard so
 * far as chips; the numbered questions (a bridge until the free engine lands); the line of what to say;
 * and the field to say it in, with Say. The world stays visible; the game does not pause.
 *
 * <p>The box keeps the same place rules as {@link TalkLayout} (the approved look when the screen leaves
 * room, wider to the left when it does not, growing up from the hotbar line, and the same last resorts
 * when the screen is too low).
 */
public final class TalkBoxLayout {

    public static final int LINE_H = TalkLayout.LINE_H;
    static final int MIN_BODY_W = 300;
    static final int MAX_BOX_W = 520;
    public static final int PORTRAIT_W = TalkLayout.PORTRAIT_W;
    public static final int PORTRAIT_H = TalkLayout.PORTRAIT_H;
    static final int PAD = 10;
    /** Space kept free above the box. */
    static final int TOP = 4;
    static final String SAY = "Say";
    static final String SAY_HINT = "Say it in words — the meaning is heard, not the exact sentence.";
    static final String HINT_MARK = "⌖ ";
    static final String ASK_ABOUT = "Ask about:";
    static final String ELLIPSIS = "…";

    public enum Kind { OPTION, CHIP, ICHIP, CLOSE, ACTION, ROLE, SAY, DEBUG, OTHER }

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
    /** The one big button for the person's trade (the sheet's action slot); null when the talk is over. */
    public Box action;
    /** The roles band: their standing in the street; null when the talk is over. */
    public Box role;
    /** What the trade carries, dim under the roles band (the sheet's items); empty when none. */
    public Text items;
    /** The mood line: how the person is, today and toward the player (the sheet's feelings line). */
    public Text mood;
    /** The small "debug" chip in the head, left of the close; opens the standing panel. */
    public Box debug;
    /** The sheet's chips (the approved prototype's chip row); the index is into view.chips. */
    public final List<Box> chips = new ArrayList<>();
    /** The control row under the field: Back through the talk, the "Ask where" pick, "Who is he?" (the register), Goodbye. */
    public Box backBtn;
    public Box findBtn;
    public Box whoBtn;
    public Box byeBtn;
    /** The portrait, as a click box: his page (the register). Null when the portrait is not drawn. */
    public Box portraitBox;
    public final List<Word> words = new ArrayList<>();
    public final List<Text> hint = new ArrayList<>();
    /** The grey line above the field ("Say it in words…"). */
    public Text sayhint;
    /** The "Ask about:" label in front of the names, or null when there are no names. */
    public Text askAbout;
    public final List<Box> boxes = new ArrayList<>();
    public int askX, askY, askW, askH;
    /** The grey hint inside the field, as long as fits (Minecraft does not shorten it). */
    public String askHint = "";
    public boolean askVisible = true;
    /** The talk is over (the person has gone): no action, no band, no field — the line and the close only. */
    public boolean ended;
    /** The thin line above the field. */
    public int ruleY;
    public int textTop, textLines, textShown, scroll;
    public int textW;
    public boolean overHotbar, compact;
    public int scrollMax;
    public boolean wholeBox;
    public int arrowX, arrowUpY, arrowDownY;
    public boolean moreAbove, moreBelow;
    public boolean fits;

    private ToIntFunction<String> font;

    private TalkBoxLayout() {
    }

    /**
     * Lays the box out. {@code font} measures text in GUI units (Minecraft's {@code Font::width});
     * {@code scroll} is the first line of the villager's words to show when they do not all fit.
     */
    public static TalkBoxLayout of(int width, int height, ToIntFunction<String> font, TalkView view, int scroll) {
        TalkBoxLayout last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            TalkBoxLayout l = new TalkBoxLayout();
            l.place(width, height, font, view, attempt >= 1, attempt >= 2, Integer.MAX_VALUE, 0);
            if (l.fits) {
                return l;
            }
            last = l;
        }
        // Still too tall: show only the lines of the villager's words that fit (at least 2); the wheel scrolls them.
        int lines = last.textShown + Math.floorDiv(last.y0 - TOP, LINE_H);
        if (lines >= 2) {
            TalkBoxLayout l = new TalkBoxLayout();
            l.place(width, height, font, view, true, true, lines, scroll);
            if (l.fits) {
                return l;
            }
        }
        // Last resort (very long lists on the smallest screens): everything in the box scrolls with the wheel.
        TalkBoxLayout l = new TalkBoxLayout();
        l.wholeBox = true;
        l.place(width, height, font, view, true, true, Integer.MAX_VALUE, scroll);
        return l;
    }

    private int tw(String s) {
        return s == null || s.isEmpty() ? 0 : font.applyAsInt(s);
    }

    /** {@code s} shortened with "…" to at most {@code w} units; empty if not even "…" fits. */
    static String fit(String s, int w, ToIntFunction<String> font) {
        int tw = s == null || s.isEmpty() ? 0 : font.applyAsInt(s);
        if (s == null || s.isEmpty() || tw <= w) {
            return s == null ? "" : s;
        }
        if (font.applyAsInt(ELLIPSIS) > w) {
            return "";
        }
        int end = s.length();
        while (end > 0) {
            end--;
            if (end > 0 && Character.isLowSurrogate(s.charAt(end))) {
                end--;
            }
            String cut = s.substring(0, end).stripTrailing() + ELLIPSIS;
            if (font.applyAsInt(cut) <= w) {
                return cut;
            }
        }
        return ELLIPSIS;
    }

    private String fit(String s, int w) {
        return fit(s, w, font);
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
        // The prototype leaves the version panel on the left (230 of 1280 pixels).
        x0 = width >= 420 ? (int) (width * (230.0d / 1280.0d)) : margin;
        int portraitSpace = PORTRAIT_W + 10;
        int need = PAD + portraitSpace + MIN_BODY_W + PAD;
        if (x1 - x0 < need) {
            x0 = Math.max(margin, x1 - need);
        }
        portrait = (x1 - x0) - 2 * PAD - portraitSpace >= 270;
        portraitX = x0 + PAD;
        bodyX = x0 + PAD + (portrait ? portraitSpace : 0);
        bodyW = Math.max(40, x1 - PAD - bodyX - (wholeBox ? 8 : 0));

        // -------- down, measured from 0 and moved into place at the end
        int y = 0;

        // Name and job (the job carries the trade's sign) on the first line, the date at its right end.
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
        // The prototype's portrait and heading form one row; the action row follows it.
        y += Math.max(headH, portrait ? PORTRAIT_H : headH) + 3;

        if (!ended) {
            String actionKind = view.actionKind == null ? "" : view.actionKind;
            int actH = compact ? 16 : 20;
            if (!actionKind.isEmpty()) {
                String actionLabel = view.actionLabel == null || view.actionLabel.isEmpty()
                        ? view.job : view.actionLabel;
                int aw = Math.min(bodyW, Math.max(40, tw(actionLabel) + 32));
                action = new Box(Kind.ACTION, -1, bodyX + bodyW - aw - 8, y, aw, actH, "", fit(actionLabel, aw - 12));
            }
            if (view.mood != null && !view.mood.isEmpty()) {
                int room = Math.max(0, bodyW - 8 - (action == null ? 0 : action.w() + 8));
                String mt = fit(view.mood, room);
                mood = new Text(mt, bodyX + 8, y + (actH - LINE_H) / 2, tw(mt));
            }
            y += actH + 3;
            // Reuse the existing scrolled text box for the prototype's dim items line.
            String itemLine = view.items == null || view.items.isEmpty() ? "" : "· " + view.items;
            if (!itemLine.isEmpty()) {
                role = new Box(Kind.ROLE, -1, bodyX, y, bodyW, LINE_H, "", fit(itemLine, bodyW));
                y += LINE_H + (compact ? 3 : 7);
            }
        }

        // What the villager says: word-wrapped, gold names keep their card id.
        textTop = y;
        boolean scrolling = maxTextLines != Integer.MAX_VALUE;
        textW = scrolling ? bodyW - 8 : bodyW;
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

        // Numbered questions in two columns (the bridge until the free engine lands): a question too long
        // for half the width gets the whole row, and the numbers stay in reading order.
        int optH = compact ? 13 : 16;
        int optGap = compact ? 2 : 3;
        int count = ended ? 0 : Math.min(9, view.options.size());
        if (count > 0) {
            int cols = (bodyW - 4) / 2 >= 100 ? 2 : 1;
            int colW = cols == 2 ? (bodyW - 4) / 2 : bodyW;
            int row = 0;
            boolean rightFree = false;
            for (int i = 0; i < count; i++) {
                String number = (i + 1) + ".";
                String full = view.options.get(i).label();
                boolean wide = cols == 1 || 5 + tw(number) + 4 + tw(full) + 4 > colW;
                if (wide && rightFree) {
                    widenLast(bodyW);
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
                widenLast(bodyW);
            }
            y += (row + (rightFree ? 1 : 0)) * (optH + optGap) + 2;
        }

        // The names heard so far, as chips.
        if (!view.names.isEmpty()) {
            int chipH = 12;
            int step = compact ? 13 : 14;
            int labelW = tw(ASK_ABOUT);
            int rowStart = bodyX + labelW + 5;
            int rowRight = bodyX + bodyW;
            int cy = y;
            if (rowRight - rowStart < 60) {
                rowStart = bodyX;
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

        // The sheet's chips (the approved prototype's chip row): what to ask next, the trade,
        // the parting — drawn where the sheet draws them, between the words and the field.
        if (!view.chips.isEmpty()) {
            int ichipH = compact ? 13 : 18;
            int istep = compact ? 14 : 22;
            int cx = bodyX;
            int cy = y;
            for (int i = 0; i < view.chips.size(); i++) {
                String label = fit(view.chips.get(i).label(), bodyW - 10);
                int w = Math.max(24, tw(label) + 12);
                if (cx + w > bodyX + bodyW && cx > bodyX) {
                    cx = bodyX;
                    cy += istep;
                }
                chips.add(new Box(Kind.ICHIP, i, cx, cy, w, ichipH, "", label));
                cx += w + 4;
            }
            y = cy + ichipH + (compact ? 5 : 8);
        }

        // The line of what to say, then the field and Say.
        int ctrlH = compact ? 14 : 20;
        int gap = 4;
        y += compact ? 3 : 5;
        ruleY = y - (compact ? 3 : 5);
        int ws = tw(SAY) + 12;
        int minAsk = 40;
        if (ended) {
            askX = bodyX;
            askY = y;
            askW = 0;
            askH = ctrlH;
            askVisible = false;
            sayhint = new Text("", bodyX, y - 12, 0);
            bottom = y + 0;
        } else {
            sayhint = new Text(fit(SAY_HINT, bodyW), bodyX, y, tw(SAY_HINT));
            y += LINE_H + (compact ? 2 : 7);
            askX = bodyX;
            askY = y;
            askW = Math.max(minAsk, bodyW - ws - gap);
            askH = ctrlH;
            boxes.add(new Box(Kind.SAY, -1, bodyX + askW + gap, y, ws, ctrlH, "", SAY));
            // The control row (the sheet's ctlrow): Back through the talk (always
            // shown, dim until there is a step to go back to), the "Ask where"
            // pick, "Who is he?" (his page), and Goodbye (the sheet's parting
            // line, not the close).
            String backLbl = "‹ Back";
            String findLbl = "Ask where… ▾";
            int wBack = tw(backLbl) + 12;
            int wWho = tw("Who is he?") + 12;
            int wBye = tw("Goodbye") + 12;
            int wFind = Math.max(24, Math.min(tw(findLbl) + 14, bodyW - wBack - wWho - wBye - 12));
            int cy2 = y + ctrlH + 4;
            int bx2 = bodyX;
            backBtn = new Box(Kind.OTHER, 5, bx2, cy2, wBack, ctrlH, "", fit(backLbl, wBack - 12));
            bx2 += wBack + 4;
            findBtn = new Box(Kind.OTHER, 1, bx2, cy2, wFind, ctrlH, "", fit(findLbl, wFind - 12));
            bx2 += wFind + 4;
            whoBtn = new Box(Kind.OTHER, 2, bx2, cy2, wWho, ctrlH, "", "Who is he?");
            bx2 += wWho + 4;
            byeBtn = new Box(Kind.OTHER, 3, bx2, cy2, Math.min(wBye, bodyX + bodyW - bx2), ctrlH, "",
                    fit("Goodbye", Math.max(24, bodyX + bodyW - bx2 - 12)));
            boxes.add(backBtn);
            boxes.add(findBtn);
            boxes.add(whoBtn);
            boxes.add(byeBtn);
            bottom = cy2 + ctrlH;
        }
        int contentH = bottom + (compact ? 6 : 10);
        for (String h : new String[]{"Say something…", "In words…", ""}) {
            if (tw(h) <= askW - 8) {
                askHint = h;
                break;
            }
        }

        // -------- sit on the hotbar line (or over the hotbar), grow upward
        int boxH = Math.max(PAD + contentH, portrait ? PORTRAIT_H + 2 * PAD : 0);
        y1 = overHotbar ? height - 4 : height - Math.max(30, (int)Math.round(height * 44.0 / 720.0));
        y0 = y1 - boxH;
        fits = y0 >= TOP;
        if (wholeBox) {
            placeScrolled(boxH, wantScroll);
            return;
        }
        int dy = y0 + PAD;
        shift(dy);
        portraitY = y0 + PAD;
        if (portrait) {
            // The portrait is his page: the click opens the register (the sheet's "Who is he?").
            portraitBox = new Box(Kind.OTHER, 4, portraitX, portraitY, PORTRAIT_W, PORTRAIT_H, "", "");
            boxes.add(portraitBox);
        }
        boxes.add(new Box(Kind.CLOSE, -1, x1 - 16, y0 + 4, 12, 11, "", "×"));
        arrowX = bodyX + textW + 2;
        arrowUpY = textTop + 1;
        arrowDownY = textTop + textShown * LINE_H - 5;
        moreAbove = scroll > 0;
        moreBelow = scroll + textShown < textLines;
    }

    private int bottom;

    /**
     * The last resort: the box fills the screen from the top to just above the bottom, and its contents
     * move up by 10 units a step. Only what is wholly inside the box (and clear of the close button) is
     * shown and can be clicked; the field hides while it is scrolled out.
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
        if (action != null && !shows.test(new int[]{action.x(), action.y(), action.w(), action.h()})) {
            action = null;
        }
        if (role != null && !shows.test(new int[]{role.x(), role.y(), role.w(), role.h()})) {
            role = null;
        }
        if (sayhint != null && !sayhint.text().isEmpty() && !shows.test(new int[]{sayhint.x(), sayhint.y(), sayhint.w(), 9})) {
            sayhint = new Text("", sayhint.x(), sayhint.y(), 0);
        }
        if (mood != null && !mood.text().isEmpty() && !shows.test(new int[]{mood.x(), mood.y(), Math.max(1, mood.w()), 9})) {
            mood = new Text("", mood.x(), mood.y(), 0);
        }
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
        // The whole-box last resort: the chip row and the control row give their ground (the box
        // scrolls instead), and the portrait keeps the face but not the page.
        chips.clear();
        backBtn = null;
        findBtn = null;
        whoBtn = null;
        byeBtn = null;
        portraitBox = null;
        boxes.add(close);
        arrowX = bodyX + bodyW + 2;
        arrowUpY = close.y() + close.h() + 3;
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
        if (sayhint != null) {
            sayhint = new Text(sayhint.text(), sayhint.x(), sayhint.y() + dy, sayhint.w());
        }
        if (mood != null) {
            mood = new Text(mood.text(), mood.x(), mood.y() + dy, mood.w());
        }
        if (askAbout != null) {
            askAbout = new Text(askAbout.text(), askAbout.x(), askAbout.y() + dy, askAbout.w());
        }
        if (action != null) {
            action = new Box(action.kind(), action.index(), action.x(), action.y() + dy, action.w(), action.h(), action.number(), action.label());
        }
        if (role != null) {
            role = new Box(role.kind(), role.index(), role.x(), role.y() + dy, role.w(), role.h(), role.number(), role.label());
        }
        if (backBtn != null) {
            backBtn = new Box(backBtn.kind(), backBtn.index(), backBtn.x(), backBtn.y() + dy, backBtn.w(), backBtn.h(), backBtn.number(), backBtn.label());
        }
        if (findBtn != null) {
            findBtn = new Box(findBtn.kind(), findBtn.index(), findBtn.x(), findBtn.y() + dy, findBtn.w(), findBtn.h(), findBtn.number(), findBtn.label());
        }
        if (whoBtn != null) {
            whoBtn = new Box(whoBtn.kind(), whoBtn.index(), whoBtn.x(), whoBtn.y() + dy, whoBtn.w(), whoBtn.h(), whoBtn.number(), whoBtn.label());
        }
        if (byeBtn != null) {
            byeBtn = new Box(byeBtn.kind(), byeBtn.index(), byeBtn.x(), byeBtn.y() + dy, byeBtn.w(), byeBtn.h(), byeBtn.number(), byeBtn.label());
        }
        if (portraitBox != null) {
            portraitBox = new Box(portraitBox.kind(), portraitBox.index(), portraitBox.x(), portraitBox.y() + dy, portraitBox.w(), portraitBox.h(), portraitBox.number(), portraitBox.label());
        }
        List<Box> cs = new ArrayList<>(chips);
        chips.clear();
        for (Box b : cs) {
            chips.add(new Box(b.kind(), b.index(), b.x(), b.y() + dy, b.w(), b.h(), b.number(), b.label()));
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
            prevSpace = TalkLayout.addTokens(tokens, src.substring(last, m.start()), null, prevSpace);
            prevSpace = TalkLayout.addTokens(tokens, m.group(2), m.group(1), prevSpace);
            last = m.end();
        }
        TalkLayout.addTokens(tokens, src.substring(last), null, prevSpace);
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
                s = fit(s, maxW);
                w = tw(s);
            }
            boolean glue = t[2] != null;
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
