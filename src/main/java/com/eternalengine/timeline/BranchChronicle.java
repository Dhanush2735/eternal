package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The running record of what a branch is, and what it is not.
 *
 * <p>A chronicle is never predicted. It accumulates as the clock advances: each year, the engine
 * asks what should have happened, what no longer happens, and why. Lines are appended as they are
 * observed — never back-filled from a computed future, because the future has not happened yet.
 *
 * <p>Every entry carries a {@link Provenance} so the player can always tell a written consequence
 * from the engine's estimate.
 */
public final class BranchChronicle {

    /** One line of branch history. */
    public record Entry(int year, String title, String body, Provenance provenance,
                        String source, String pinId) {

        public Entry {
            title = title == null ? "" : title;
            body = body == null ? "" : body;
            source = source == null ? "" : source;
            pinId = pinId == null ? "" : pinId;
            provenance = provenance == null ? Provenance.MODELLED : provenance;
        }

        public boolean hasSource() {
            return !source.isBlank();
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Append a line. Entries are kept in the order observed. */
    public synchronized void add(Entry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    public synchronized void add(int year, String title, String body, Provenance provenance) {
        add(new Entry(year, title, body, provenance, "", ""));
    }

    /**
     * Record that a pin stopped happening. Always {@link Provenance#OBSERVED} — the world showing
     * nothing is not a claim about history, it is the absence of an event.
     */
    public synchronized void recordAbsence(int year, String pinId, String pinName) {
        add(new Entry(
                year,
                "Nothing happened here",
                "\"" + pinName + "\" does not occur in this branch. The place is quiet, and nobody "
                        + "knows what they were spared.",
                Provenance.OBSERVED, "", pinId));
    }

    /**
     * Record that sibling pins went down with the main one. {@link Provenance#STRUCTURAL}: this is
     * data identity, not authored causation.
     */
    public synchronized void recordSiblings(int year, List<String> siblingIds) {
        if (siblingIds == null || siblingIds.isEmpty()) {
            return;
        }
        add(new Entry(
                year,
                "The same night, the same people",
                siblingIds.size() + " other site(s) of the same event do not happen either: "
                        + String.join(", ", siblingIds),
                Provenance.STRUCTURAL, "", ""));
    }

    /** Everything observed up to and including {@code year}. */
    public synchronized List<Entry> upTo(int year) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.year() <= year) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized void clear() {
        entries.clear();
    }

    /** Earliest and latest years present, or {@code null} when empty. */
    public synchronized int firstYear() {
        int min = Integer.MAX_VALUE;
        for (Entry e : entries) {
            min = Math.min(min, e.year());
        }
        return entries.isEmpty() ? 0 : min;
    }

    public synchronized int lastYear() {
        int max = Integer.MIN_VALUE;
        for (Entry e : entries) {
            max = Math.max(max, e.year());
        }
        return entries.isEmpty() ? 0 : max;
    }

    /** Count of entries by provenance — used for the honesty report given to the player. */
    public synchronized int countOf(Provenance provenance) {
        int n = 0;
        for (Entry e : entries) {
            if (e.provenance() == provenance) {
                n++;
            }
        }
        return n;
    }

    /**
     * Player-facing summary of how trustworthy this branch is. The point is to make the mix
     * visible rather than to score it.
     */
    public synchronized String trustSummary() {
        if (entries.isEmpty()) {
            return "No divergence recorded — this is the original timeline.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§6Chronicle:§r ").append(entries.size()).append(" entries");
        for (Provenance p : Provenance.values()) {
            int n = countOf(p);
            if (n > 0) {
                sb.append(" §8|§r ").append(p.colour()).append(n).append(" ").append(p.label());
            }
        }
        return sb.toString();
    }

    /** Rehydrate from persistence without replaying the world. */
    public synchronized void restore(List<Entry> restored) {
        entries.clear();
        if (restored != null) {
            for (Entry e : restored) {
                if (e != null) {
                    entries.add(e);
                }
            }
        }
    }
}
