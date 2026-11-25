package com.eternalengine.client.gui.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapped text where names ({@code [[cardId|shown words]]}) have their own colour. Pure Java, for the screens and
 * the layout probe.
 */
public final class Rich {

    private static final Pattern LINK = Pattern.compile("\\[\\[(\\w+)\\|([^\\]]+)\\]\\]");

    /** A word, whether it is part of a name, and whether it sticks to the word before (no space: "Suryapet."). */
    public record Word(String text, boolean name, boolean glue) {
        public Word(String text, boolean name) {
            this(text, name, false);
        }
    }

    private Rich() {
    }

    public static List<Word> words(String s) {
        List<Word> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        Matcher m = LINK.matcher(s);
        int at = 0;
        while (m.find()) {
            split(s.substring(at, m.start()), false, out, at > 0 ? s.charAt(at - 1) : ' ');
            split(m.group(2), true, out, m.start() > 0 ? s.charAt(m.start() - 1) : ' ');
            at = m.end();
        }
        split(s.substring(at), false, out, at > 0 ? s.charAt(at - 1) : ' ');
        return out;
    }

    /**
     * The words of one piece. Its first word sticks to the word before when nothing separates them (a full stop right
     * after a name, or a name right after an opening bracket).
     */
    private static void split(String s, boolean name, List<Word> out, char before) {
        if (s.isEmpty()) {
            return;
        }
        boolean first = true;
        boolean startsBare = !Character.isWhitespace(s.charAt(0));
        for (String w : s.split("\\s+")) {
            if (w.isEmpty()) {
                first = false;
                continue;
            }
            boolean glue = first && startsBare && !out.isEmpty() && !Character.isWhitespace(before);
            out.add(new Word(w, name, glue));
            first = false;
        }
    }

    /** The text wrapped to a width: lines of words. */
    public static List<List<Word>> wrap(Canvas c, String s, int w) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        int lineW = 0;
        int space = c.width(" ");
        for (Word word : words(s)) {
            int ww = c.width(word.text());
            int gap = word.glue() ? 0 : space;
            if (!line.isEmpty() && lineW + gap + ww > w) {
                lines.add(line);
                line = new ArrayList<>();
                lineW = 0;
            }
            if (line.isEmpty() && ww > w) {
                // One word wider than the line: cut it.
                line.add(new Word(c.fit(word.text(), w), word.name()));
                lines.add(line);
                line = new ArrayList<>();
                continue;
            }
            lineW += (line.isEmpty() ? 0 : gap) + ww;
            line.add(word);
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /** Draw wrapped text from (x, y); returns the y below it. Lines above {@code clipTop} or below {@code clipBottom} are skipped. */
    public static int draw(Canvas c, List<List<Word>> lines, int x, int y, int color, int nameColor, int clipTop,
                           int clipBottom, boolean bold) {
        int space = c.width(" ");
        for (List<Word> line : lines) {
            if (y >= clipTop && y + Canvas.LINE_H <= clipBottom) {
                int cx = x;
                boolean firstWord = true;
                for (Word w : line) {
                    if (!firstWord && w.glue()) {
                        cx -= space + (bold ? 1 : 0);
                    }
                    firstWord = false;
                    if (bold) {
                        c.bold(w.text(), cx, y, w.name() ? nameColor : color);
                    } else {
                        c.text(w.text(), cx, y, w.name() ? nameColor : color, false);
                    }
                    cx += c.width(w.text()) + space + (bold ? 1 : 0);
                }
            }
            y += Canvas.LINE_H;
        }
        return y;
    }

    /** The plain words of a text (names without their marks). */
    public static String plain(String s) {
        return s == null ? "" : LINK.matcher(s).replaceAll("$2");
    }
}
