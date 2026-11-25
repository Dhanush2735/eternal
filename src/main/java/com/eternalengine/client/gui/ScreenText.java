package com.eternalengine.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/** Width-aware plain text wrapping shared by the interaction screens. */
final class ScreenText {
    private ScreenText() {}

    static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        return wrap(text, i -> width, measure);
    }

    /** Wraps with a width per line, so the first line can be narrower than the rest. */
    static List<String> wrap(String text, IntUnaryOperator widthForLine, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        int lineNo = 0;
        for (String word : text.trim().split("\\s+")) {
            int w = Math.max(1, widthForLine.applyAsInt(lineNo));
            String next = line.length() == 0 ? word : line + " " + word;
            if (measure.applyAsInt(next) <= w) {
                line.setLength(0);
                line.append(next);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                lineNo++;
                w = Math.max(1, widthForLine.applyAsInt(lineNo));
            }
            String rest = word;
            while (!rest.isEmpty() && measure.applyAsInt(rest) > w) {
                int end = 0, best = 0;
                while (end < rest.length()) {
                    end += Character.charCount(rest.codePointAt(end));
                    if (measure.applyAsInt(rest.substring(0, end)) > w) {
                        break;
                    }
                    best = end;
                }
                if (best == 0) {
                    best = Character.charCount(rest.codePointAt(0));
                }
                lines.add(rest.substring(0, best));
                rest = rest.substring(best);
                lineNo++;
                w = Math.max(1, widthForLine.applyAsInt(lineNo));
            }
            line.append(rest);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
