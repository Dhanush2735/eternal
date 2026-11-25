package com.eternalengine.story;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.eternalengine.timeline.Provenance;

/**
 * <b>G4 and G10: a story about a place, told by somebody, out of what the record holds.</b>
 *
 * <h2>What this is not</h2>
 *
 * <p>It invents nothing. There is no event here that the packs do not date, no price, no population figure and
 * no person the record does not name. A story is an <b>ordered reading of rows that exist</b>, and every
 * sentence carries the row it came from, which is why {@link Sentence#record()} is not optional.
 *
 * <h2>The two ideas, and they are G4 and G10</h2>
 *
 * <p><b>A story is ordered by time and named in the language of its own year.</b> Bombay in 1900 and Mumbai in
 * 1990 are the same place, and a story that calls it Mumbai in 1700 is wrong in the way this whole project
 * exists to avoid. So the pack's own dated name is used for every sentence: 3,892 of the 30,151 towns carry a
 * name by era, and 2,582 of those date it, which is the number a story can actually tell in.
 *
 * <p><b>A story is told by a voice, and the voice changes the story.</b> The same rows, told by a court
 * chronicler and by a tax roll, read differently, and the difference is the point: a card with one answer per
 * question cannot say that two people in one square disagree. {@link Voice} is that, and the four voices are a
 * sample of the thousands a real archive holds.
 */
public final class BranchStory {

    /** Four kinds of narrator, each with what they overstate. G10, in four rows. */
    public enum Voice {
        /** Kept by the winner, and it records a defeat as a withdrawal. */
        COURT("a chronicler at court", "the throne, and the ruler in it",
                "a defeat becomes a withdrawal in good order, and a retreat becomes a regrouping"),
        /** The most reliable thing in an archive, and not a story at all. */
        TAX_ROLL("a clerk of the tax roll", "what can be counted and collected",
                "it has no interest in glory and no interest in famine, so a hard year looks like a short list"),
        /** Written by a priest, and every good thing has a divine cause. */
        TEMPLE("a priest", "the god, the temple and its lands",
                "a good harvest is a reward and a bad one is a lesson, and the king's tank was dug by the temple"),
        /** The only account from outside, and wrong about half of it. */
        TRAVELLER("a traveller", "the market, the road and whatever is strange here",
                "it is astonished by ordinary things and misses what everybody takes for granted");

        private final String who;
        private final String serves;
        private final String overstates;

        Voice(String who, String serves, String overstates) {
            this.who = who;
            this.serves = serves;
            this.overstates = overstates;
        }

        public String who() {
            return who;
        }

        public String serves() {
            return serves;
        }

        public String overstates() {
            return overstates;
        }

        /** True for the voice that is not telling a story: a roll is a list and says so. */
        public boolean isRecord() {
            return this == TAX_ROLL;
        }
    }

    /**
     * What a story can be made of, which is a dated row from a pack. {@code endYear} equal to {@code year}
     * means a thing that happened; a later one means something that stood.
     */
    public record Fact(int year, Integer endYear, String name, String kind, String record,
                       Provenance provenance) {
        public Fact {
            name = name == null ? "" : name;
            kind = kind == null || kind.isEmpty() ? "thing" : kind;
            record = record == null ? "" : record;
            provenance = provenance == null ? Provenance.OBSERVED : provenance;
        }

        /**
         * A row whose year the pack records exactly. Kept as a constructor of its own so that every caller
         * written before this field existed still compiles, and so that the usual case is the short one.
         */
        public Fact(int year, Integer endYear, String name, String kind, String record) {
            this(year, endYear, name, kind, record, Provenance.OBSERVED);
        }

        public boolean stands() {
            return endYear != null && endYear > year;
        }

        public int until() {
            return endYear == null ? year : endYear;
        }
    }

    /** One sentence of a story, with the row behind it and how sure the story is of it. */
    public record Sentence(int year, String text, Voice voice, String record, Provenance provenance) {
        public String line() {
            return year + ": " + text;
        }
    }

    /** A story: a place, one of its own names, a voice, and the sentences in order. */
    public record Story(String place, String name, int year, Voice voice, List<Sentence> sentences,
                        String refused) {

        public boolean empty() {
            return sentences.isEmpty();
        }

        /** The story as a reader gets it, one sentence a line. */
        public List<String> lines() {
            List<String> out = new ArrayList<>();
            String who = voice.who();
            out.add(name() + " in " + year + ", as " + who + " would have it. "
                    + Character.toUpperCase(who.charAt(0)) + who.substring(1) + " serves " + voice.serves()
                    + ", and what that costs is this: " + voice.overstates() + ".");
            for (Sentence s : sentences) out.add(s.line());
            return out;
        }

        public String asText() {
            return String.join("\n", lines());
        }

        /**
         * The same reading with every sentence that does not rest on an exact record marked as such. A year
         * taken from a century of founding, or from the technology atlas rather than from Wikidata, is not the
         * same claim as a year recorded on the day, and a reader in the world is entitled to see which is which.
         */
        public List<String> markedLines() {
            List<String> out = new ArrayList<>();
            List<String> plain = lines();
            if (!plain.isEmpty()) out.add(plain.get(0));
            for (Sentence s : sentences) {
                out.add(s.line() + (s.provenance() == Provenance.OBSERVED
                        ? "" : "  (" + s.provenance().label() + ")"));
            }
            return out;
        }

        public String asMarkedText() {
            return String.join("\n", markedLines());
        }
    }

    private BranchStory() {
    }

    /**
     * Build a story about a place in a year, from facts that are already dated.
     *
     * @param place     the place as the packs name it today
     * @param nameThen  the place's name in the year asked about, from the pack's own dated names
     * @param year      the year the story is told in
     * @param voice     who is telling it
     * @param facts     the dated rows near the place, any order
     * @param limit     how many sentences at most
     */
    public static Story tell(String place, String nameThen, int year, Voice voice, List<Fact> facts, int limit) {
        List<Sentence> out = new ArrayList<>();
        List<Fact> ordered = new ArrayList<>(facts == null ? List.of() : facts);
        // Only what the story can stand on: dated, named, and not in its own future.
        ordered.removeIf(f -> f == null || f.name().isEmpty() || f.year() > year);
        ordered.sort(Comparator.comparingInt(Fact::year).thenComparing(Fact::name));

        for (Fact f : ordered) {
            if (out.size() >= limit) break;
            out.add(new Sentence(f.year(), say(f, year, voice), voice, f.record(), f.provenance()));
        }
        if (out.isEmpty()) {
            return new Story(place, nameThen, year, voice, List.of(),
                    "Nothing here is dated before " + year + ", so there is no story to tell. That is not a "
                            + "claim that nothing happened: it is what the record holds.");
        }
        return new Story(place, nameThen, year, voice, out, "");
    }

    /**
     * One row, said in the voice's own way. The <b>fact</b> never changes: only the words around it, and the
     * sentence always contains the row's own name and date, so a reader can check it.
     */
    static String say(Fact f, int year, Voice voice) {
        if (voice == Voice.TAX_ROLL) {
            return f.stands()
                    ? f.name() + " stands, and has stood since " + f.year() + "."
                    : f.name() + " is entered for " + f.year() + ", and entered plainly, without a word "
                            + "about whether it was just or cruel.";
        }
        if (f.stands()) {
            int age = year - f.year();
            return switch (voice) {
                case COURT -> f.name() + " has stood for " + age + " year" + (age == 1 ? "" : "s")
                        + ", raised by the throne, and it is a credit to it that it still stands.";
                case TEMPLE -> f.name() + " has stood since " + f.year() + ", and it stands because it was "
                        + "wanted by those who were heard.";
                case TRAVELLER -> f.name() + ", standing since " + f.year() + ", and there is nothing like it "
                        + "where I come from, though I may be wrong about that.";
                default -> f.name() + " has stood since " + f.year() + ".";
            };
        }
        return switch (voice) {
            case COURT -> f.name() + ", in " + f.year() + ": the throne's business was done, and it was done "
                    + "in good order, whatever was said afterwards.";
            case TEMPLE -> f.name() + ", in " + f.year() + ": and afterwards there were prayers, because "
                    + "nothing of this size happens without them.";
            case TRAVELLER -> switch (f.kind()) {
                case "line" -> f.name() + ", in " + f.year() + ": they laid rails where the carts went, and "
                        + "the carts still go, which nobody here finds strange but I do.";
                case "port" -> f.name() + ", in " + f.year() + ", and the whole town smells of what comes "
                        + "off the water, which I was not warned about.";
                case "works" -> f.name() + ", in " + f.year() + ", and the noise of it carries further than "
                        + "the building does.";
                default -> f.name() + ", in " + f.year() + ", which the people here talk of as though it were "
                        + "the beginning of the world, and they talk of little else.";
            };
            default -> f.name() + " in " + f.year() + ".";
        };
    }

    /**
     * <b>G10, the part that matters: the same fact in two voices.</b> Two sentences about one row, and the
     * difference between them is what a card with one answer per question cannot show.
     */
    public static List<String> twoAccounts(Fact f, Voice a, Voice b) {
        List<String> out = new ArrayList<>();
        out.add(a.who() + ": " + say(f, f.year(), a));
        out.add(b.who() + ": " + say(f, f.year(), b));
        out.add("Both are about one row of the record (" + f.record() + "). Where the record has one account, "
                + "the second voice is a reading of the same rows and not a second source.");
        return out;
    }

    /**
     * A branch's own knowledge: something a player did that the record does not have. It is
     * {@link Provenance#AUTHORED}, it names the player, and it is <b>never</b> mixed into the record's own
     * sentences without a mark.
     */
    public static Sentence authored(int year, String text, String player, Voice voice) {
        return new Sentence(year, text + " That is " + player + "'s account of it and not the record's.",
                voice, "nothing: this is not in the packs", Provenance.AUTHORED);
    }

    /** A rumour beside an authored sentence, which is what the rest of the square says about it. */
    public static Sentence rumour(int year, String subject, Voice voice) {
        return new Sentence(year, "They say " + subject + " is not what " + voice.who() + " says it is. Nobody "
                + "has looked.", voice, "nothing: a rumour has no row behind it", Provenance.MODELLED);
    }

    /**
     * The story a card cannot tell: what this place is called, in this year, in its own language of the time.
     * Kept separate from the sentences because it is a fact about the name and not part of the account.
     */
    public static String namingLine(String nameNow, String nameThen, int year, Integer renamedAt) {
        if (nameNow == null || nameThen == null || nameNow.equals(nameThen)) {
            return nameNow + " is called " + nameNow + " in " + year + ".";
        }
        if (renamedAt != null && renamedAt > year) {
            return "It is " + nameThen + " in " + year + ", and " + nameNow + " is what it is called now: the "
                    + "record dates the change to " + renamedAt + ".";
        }
        return "It is " + nameThen + " in " + year + ", which is the name this record gives it for that year.";
    }

    /** The voices, for a screen or a probe. Four rows and what each one is not to be trusted about. */
    public static List<String> voices() {
        List<String> out = new ArrayList<>();
        for (Voice v : Voice.values()) {
            out.add(v.who() + " serves " + v.serves() + ". What that costs: " + v.overstates() + ".");
        }
        return out;
    }

    /** One line, for a screen, that says what a story here is. */
    public static String ruleLine() {
        return "A story here is read out of the record: every sentence comes from a dated row, and it is told "
                + "in the name the place had that year, by a voice that has something to gain.";
    }
}
