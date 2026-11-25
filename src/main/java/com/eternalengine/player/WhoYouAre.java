package com.eternalengine.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>Who you are: the card a player starts with.</b> F10.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 52's survey found a story-campaign start and a "time traveller view" for the book, and nothing else:
 * no start place, no family line, no kit, and no way to ask the one question the grandfather paradox (step 53)
 * is built on: <em>was this person alive in that year, and are they mine?</em>
 *
 * <p>So this class is the card: a name, whether you are a local or a traveller, the year and place you begin
 * in, the line of people you come from, and what you carry. Nothing more.
 *
 * <h2>The line is recorded once and never edited</h2>
 *
 * <p>The world may end a life; it may not rewrite a birth. A line is checked when the card is made (a parent
 * born after their child is refused, a death before a birth is refused, two relatives with one name are
 * refused) and after that it stands. Every question this class answers about the line is read from that fixed
 * record, so step 53 can trust it.
 *
 * <h2>A traveller's line is carried, not recorded</h2>
 *
 * <p>A local's line belongs to this world's record. A traveller brings theirs with them: it may name people
 * this world has never heard of, and whether it matches anyone here is a question for the record, not a fact
 * this class invents. Both kinds keep their line; only the <em>origin</em> of the line differs, and the card
 * says which it is every time it is printed.
 */
public final class WhoYouAre {

    /** <b>Local or traveller.</b> F10's central choice, because the two answer "where is your family" differently. */
    public enum Kind {
        LOCAL("a local", "the record holds this line"),
        TRAVELLER("a time traveller", "carried with you, matching this world's record is a question for the record");

        private final String word;
        private final String lineOrigin;

        Kind(String word, String lineOrigin) {
            this.word = word;
            this.lineOrigin = lineOrigin;
        }

        public String word() {
            return word;
        }

        public String lineOrigin() {
            return lineOrigin;
        }
    }

    /**
     * <b>One person you come from.</b> {@code depth} is how many steps up: 1 is a parent, 2 a grandparent.
     * {@code died} is null for someone the record does not end.
     */
    public record Entry(String name, String relation, int depth, int born, Integer died) {

        /** Is this person alive in the given year? The question the paradox needs. */
        public boolean aliveAt(int year) {
            return born <= year && (died == null || died >= year);
        }

        public String line() {
            return name() + " (" + relation() + ", " + born() + (died == null ? "" : "-" + died) + ")";
        }
    }

    /** <b>One thing you carry.</b> Counts are whole and at least one: a kit of nothing is no kit. */
    public record Item(String name, int count) {
    }

    /**
     * <b>The outcome of making a card.</b> Either a card and no refusals, or no card and every reason why,
     * because a card half-made is the worst of the two.
     */
    public record Result(WhoYouAre who, List<String> refused) {
        public boolean ok() {
            return who != null;
        }
    }

    private final String name;
    private final Kind kind;
    private final int born;
    private final int startYear;
    private final String startPlace;
    private final List<Entry> line;
    private final List<Item> kit;

    private WhoYouAre(String name, Kind kind, int born, int startYear, String startPlace,
                      List<Entry> line, List<Item> kit) {
        this.name = name;
        this.kind = kind;
        this.born = born;
        this.startYear = startYear;
        this.startPlace = startPlace;
        this.line = line;
        this.kit = kit;
    }

    /**
     * <b>Make a card, or say why not.</b> Every rule this class holds is checked here, once, so that
     * everything after it can read without doubting.
     */
    public static Result make(String name, Kind kind, int born, int startYear, String startPlace,
                              List<Entry> line, List<Item> kit) {
        List<String> refused = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) {
            refused.add("a card with no name is nobody's");
        }
        if (kind == null) {
            refused.add("a card must say whether you are a local or a traveller");
        }
        if (startPlace == null || startPlace.trim().isEmpty()) {
            refused.add("a card with no start place begins nowhere");
        }
        // A local cannot begin before they are born. A traveller can: that is what travelling is.
        if (kind == Kind.LOCAL && born > startYear) {
            refused.add("a local cannot begin in " + startYear + ", which is before they were born in " + born);
        }

        List<Entry> keep = new ArrayList<>();
        for (Entry e : (line == null ? List.<Entry>of() : line)) {
            if (e == null) {
                refused.add("an empty name in the line is nobody");
                continue;
            }
            if (e.name() == null || e.name().trim().isEmpty()) {
                refused.add("an empty name in the line is nobody");
                continue;
            }
            if (e.relation() == null || e.relation().trim().isEmpty()) {
                refused.add("the line must say what " + e.name() + " is to you");
                continue;
            }
            if (e.depth() < 1) {
                refused.add(e.name() + " is at depth " + e.depth() + ", and depth 1 is as near as a line goes");
                continue;
            }
            if (e.died() != null && e.died() < e.born()) {
                refused.add(e.name() + " died in " + e.died() + ", which is before they were born in " + e.born());
                continue;
            }
            if (e.born() >= born) {
                refused.add(e.name() + " was born in " + e.born() + ", which is not before you were born in " + born);
                continue;
            }
            keep.add(e);
        }

        // One name in a line, once. Two grandmothers named Mary are two grandmothers and the line must say
        // which is which; two entries called by one name are a question, not a family.
        Set<String> seenNames = new LinkedHashSet<>();
        for (Entry e : keep) {
            String key = e.name().trim().toLowerCase(java.util.Locale.ROOT);
            if (!seenNames.add(key)) {
                refused.add("the line holds 2 people by the name " + e.name() + "; say which is which");
            }
        }

        // The farther up the line, the earlier the birth. A grandparent born after their own child is a line
        // that cannot have happened, and step 53 would answer questions from it.
        for (Entry a : keep) {
            for (Entry b : keep) {
                if (a.depth() > b.depth() && a.born() >= b.born()) {
                    refused.add(a.name() + " (" + a.relation() + ", born " + a.born() + ") cannot be "
                            + b.name() + "'s ancestor: " + b.name() + " was born in " + b.born());
                }
            }
        }

        // A near relation and a far one at one depth is a line that does not know what it is.
        Set<Integer> depths = new LinkedHashSet<>();
        for (Entry e : keep) {
            if (!depths.add(e.depth())) {
                refused.add("two people in the line share depth " + e.depth() + "; one step of the line is one person");
            }
        }

        List<Item> items = new ArrayList<>();
        Set<String> seenItems = new LinkedHashSet<>();
        for (Item i : (kit == null ? List.<Item>of() : kit)) {
            if (i == null || i.name() == null || i.name().trim().isEmpty()) {
                refused.add("the kit holds a thing with no name");
                continue;
            }
            if (i.count() < 1) {
                refused.add("the kit holds " + i.count() + " of " + i.name() + ", which is none");
                continue;
            }
            if (!seenItems.add(i.name().trim().toLowerCase(java.util.Locale.ROOT))) {
                refused.add("the kit names " + i.name() + " twice; one line per thing");
                continue;
            }
            items.add(new Item(i.name().trim(), i.count()));
        }

        if (!refused.isEmpty()) {
            return new Result(null, refused);
        }
        List<Entry> ordered = new ArrayList<>(keep);
        ordered.sort(Comparator.comparingInt(Entry::depth));
        return new Result(new WhoYouAre(name.trim(), kind, born, startYear, startPlace.trim(),
                List.copyOf(ordered), List.copyOf(items)), List.of());
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public int born() {
        return born;
    }

    public int startYear() {
        return startYear;
    }

    public String startPlace() {
        return startPlace;
    }

    /** The line, nearest first, checked once and never edited. */
    public List<Entry> line() {
        return line;
    }

    /** Where the line comes from: the record's, or carried here from somewhere else. */
    public String lineOrigin() {
        return kind.lineOrigin();
    }

    public List<Item> kit() {
        return kit;
    }

    /** Are you alive in the given year? A traveller is alive in any year they stand in. */
    public boolean aliveAt(int year) {
        return kind == Kind.TRAVELLER || born <= year;
    }

    /** The people of your line alive in the given year, nearest first. */
    public List<Entry> ancestorsAliveAt(int year) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : line) {
            if (e.aliveAt(year)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Is this name one of your ancestors? Exact and case-insensitive after trimming, never fuzzy. */
    public boolean isAncestor(String who) {
        return depthOf(who) > 0;
    }

    /** How many steps up this name is, or 0 if they are not in your line. */
    public int depthOf(String who) {
        if (who == null) {
            return 0;
        }
        String key = who.trim().toLowerCase(java.util.Locale.ROOT);
        for (Entry e : line) {
            if (e.name().trim().toLowerCase(java.util.Locale.ROOT).equals(key)) {
                return e.depth();
            }
        }
        return 0;
    }

    /**
     * <b>The chain from one ancestor down to you.</b> That ancestor first, every nearer relation after them,
     * you last (you are not an entry of your own line), so a reader can see who must be born for you to be
     * born. The first version of this method walked the wrong way and returned the ancestors ABOVE the one
     * asked for, which is exactly the list step 53 must not be given. Step 53's grandfather question is this
     * list plus a year.
     */
    public List<Entry> chainToYou(String ancestor) {
        int depth = depthOf(ancestor);
        if (depth == 0) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : line) {
            if (e.depth() <= depth) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingInt(Entry::depth).reversed());
        return out;
    }

    /** The card, as a player reads it: name, kind, where and when you begin, the line, and the kit. */
    public List<String> card() {
        List<String> out = new ArrayList<>();
        out.add(name + " · " + kind.word() + " · begins in " + startYear + " at " + startPlace
                + " · born " + born);
        if (line.isEmpty()) {
            out.add("  the line: nobody is recorded" + (kind == Kind.TRAVELLER
                    ? " (" + lineOrigin() + ")" : ""));
        } else {
            out.add("  the line (" + lineOrigin() + "):");
            for (Entry e : line) {
                out.add("    " + e.line());
            }
        }
        if (kit.isEmpty()) {
            out.add("  the kit: nothing");
        } else {
            StringBuilder k = new StringBuilder("  the kit:");
            for (Item i : kit) {
                k.append(" ").append(i.count()).append(" ").append(i.name()).append(",");
            }
            out.add(k.substring(0, k.length() - 1));
        }
        return out;
    }

    public static String ruleLine() {
        return "A card is made once and never edited: the world may end a life and may not rewrite a birth. "
                + "A local's line is this world's record; a traveller carries theirs with them.";
    }
}
