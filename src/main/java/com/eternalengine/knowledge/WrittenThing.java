package com.eternalengine.knowledge;

import com.eternalengine.civilization.Signs;
import com.eternalengine.story.BranchStory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>G4, the part a player actually walks up to: the written object in the world.</b>
 *
 * <h2>Why this is not a book</h2>
 *
 * <p>The obvious thing to build is a book, and the record says no. The mod already knows, from step 43, what
 * writing exists at a place in a year and what kinds of thing are written there: an <b>edict cut in the rock</b>,
 * a <b>boundary stone</b>, the <b>inscription on the temple</b>, the <b>cut stone at the grave</b>, a
 * <b>milestone</b>, <b>graffiti</b>, the <b>sign over the shop</b>, a <b>posted notice</b>, a <b>street name</b>,
 * a <b>road sign</b>, an <b>official notice in two scripts</b>. Not one of them is a book, and a book in Cuzco in
 * 1500 is simply wrong: {@code Signs.hereAt} answers <i>"Nothing is written here: what is known is remembered and
 * told."</i>
 *
 * <p>So the written object is a <b>surface that exists at that place in that year</b>, taken from the mod's own
 * {@code scripts.json.gz}, and it carries a story on it. The surface decides three things the object cannot
 * decide for itself:
 *
 * <ul>
 *   <li><b>whether there is anything to write on at all</b>, because a place with no writing gets no object;</li>
 *   <li><b>how much fits</b>, because a gravestone holds a name and two dates and not a chronicle. What does not
 *       fit is not on the stone, and the object says how much was left off rather than shrinking the truth;</li>
 *   <li><b>whose wall it is</b>, because an edict is the ruler's, a temple inscription is the temple's and a
 *       posted notice is the administration's. A story in somebody else's voice on that wall is a different act
 *       from a story in the right one, and the text says which it is.</li>
 * </ul>
 *
 * <h2>What it will not do</h2>
 *
 * <p>It adds no sentence of its own about the past. Everything between the opening line and the closing line is
 * {@link BranchStory.Story#markedLines()}, which is to say rows of the record with their provenance marks kept,
 * or a branch's own claims with theirs. An inscription has a voice in the modern sense only because the story
 * does.
 */
public final class WrittenThing {

    /**
     * How many lines a surface holds. <b>A judgement, not a measurement.</b> A gravestone is not a chronicle and
     * a temple wall is not a book; the numbers below are what this project decided, and a different game could
     * decide differently without being wrong.
     */
    private static final int GRAVE_ROOM = 3;
    private static final int STONE_ROOM = 3;
    private static final int WALL_ROOM = 12;
    private static final int SCRATCH_ROOM = 5;
    private static final int BOARD_ROOM = 2;

    private final Signs.Sign surface;
    private final String placeNow;
    private final String placeThen;
    private final int year;
    private final String script;
    private final BranchStory.Voice voice;
    private final String author;
    private final List<String> lines;

    private WrittenThing(Signs.Sign surface, String placeNow, String placeThen, int year, String script,
                         BranchStory.Voice voice, String author, List<String> lines) {
        this.surface = surface;
        this.placeNow = placeNow;
        this.placeThen = placeThen;
        this.year = year;
        this.script = script;
        this.voice = voice;
        this.author = author;
        this.lines = List.copyOf(lines);
    }

    /**
     * The written object a player finds: a real surface at a real place in a real year, with a story cut into it.
     *
     * @param sign   the kind of surface, from {@code Signs.signKinds()}
     * @param here   what is written at this place in this year, from {@code Signs.hereAt}
     * @param story  the story, already read out of the record, already carrying its marks
     * @param author who had it put up, for a screen to name
     */
    public static WrittenThing carve(Signs.Sign sign, Signs.Here here, BranchStory.Story story, String author) {
        if (sign == null || here == null || story == null) {
            throw new IllegalArgumentException("a written thing needs a surface, a place and a story");
        }
        if (here.year() != story.year()) {
            // The writing at a place is worked out for a year, and the story is told in a year, and if those are
            // not the same year then the object is one thing dated as another. The first version of this class
            // checked the writing for `here` and took the date from `story`, and the probe printed a grave at
            // Cuzco "in 1500" cut in the Latin alphabet, which reached Peru in 1576. A disagreement between the
            // two years is a mistake in the caller, not a fact about the world.
            throw new IllegalStateException("the writing here is worked out for " + here.year() + " and the story "
                    + "is told in " + story.year() + ": one of them is the wrong year, and a surface cannot be "
                    + "carved in a year when this place had no writing.");
        }
        if (!here.writing()) {
            throw new IllegalStateException("Nothing is written at " + story.place() + " in " + story.year()
                    + ", so nothing can be written there: " + here.line() + " A carved sentence here would be an "
                    + "invention, and inventions do not live in this pack.");
        }
        if (!present(here, sign)) {
            throw new IllegalStateException("the " + sign.name() + " is not put up at " + story.place() + " in "
                    + story.year() + ": the record does not have one there, and the object cannot invent it.");
        }
        if (story.empty()) {
            throw new IllegalArgumentException("a surface with nothing dated on it is a blank stone, not an object");
        }
        List<String> body = new ArrayList<>();
        body.add(sign.name() + " at " + story.name() + ", " + story.year()
                + (here.script() == null ? "" : ", cut in " + here.script()) + ".");
        body.addAll(story.markedLines().subList(1, story.markedLines().size()));
        body.add(closing(story.voice(), whoseWall(sign)));
        return new WrittenThing(sign, story.place(), story.name(), story.year(), here.script(),
                story.voice(), author == null ? "" : author, body);
    }

    /**
     * Whose wall this kind of surface is. {@code null} where none of the four voices puts this kind of thing up: a
     * gravestone is a family's, graffiti is the street's, and a shop sign is a shopkeeper's, and calling any of
     * them a chronicle would be a lie about who wrote it.
     *
     * <p>The ids are the ones in {@code scripts.json.gz} and are the same strings {@code Signs} reports, so a
     * renamed kind in the pack shows up here as "nobody's wall" rather than as a silent wrong answer.
     */
    public static BranchStory.Voice whoseWall(Signs.Sign sign) {
        return switch (sign.id()) {
            case "edict" -> BranchStory.Voice.COURT;
            case "temple_inscription" -> BranchStory.Voice.TEMPLE;
            case "notice", "milestone", "boundary", "official_bilingual" -> BranchStory.Voice.TAX_ROLL;
            default -> null;
        };
    }

    private static String closing(BranchStory.Voice voice, BranchStory.Voice wall) {
        String head = "Every line above is dated, and a mark says which of them rests on an estimate and not on a "
                + "record.";
        if (wall == null) {
            return head + " This is not the kind of thing " + voice.who() + " puts up, and it says so rather than "
                    + "pretending otherwise.";
        }
        if (wall == voice) {
            return head + " It is the kind of thing " + voice.who() + " puts up, which is why it reads as it does.";
        }
        return head + " A " + voice.who() + " did not put this up: the wall belongs to " + wall.who()
                + ", and the words on it were paid for by somebody else.";
    }

    private static boolean present(Signs.Here here, Signs.Sign sign) {
        for (Signs.Sign s : here.signs()) {
            if (s.id().equals(sign.id())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------- what is on it

    public Signs.Sign surface() {
        return surface;
    }

    public int year() {
        return year;
    }

    public String placeNow() {
        return placeNow;
    }

    /** The name the place had that year, which is the only name the object uses. */
    public String placeThen() {
        return placeThen;
    }

    public String script() {
        return script;
    }

    public BranchStory.Voice voice() {
        return voice;
    }

    public String author() {
        return author;
    }

    /** Every line the story would put on the surface, whether or not the surface holds them. */
    public List<String> lines() {
        return lines;
    }

    /** How many lines this surface holds, before anything is left off. */
    public int room() {
        return roomOf(surface);
    }

    static int roomOf(Signs.Sign s) {
        return switch (s.id()) {
            case "grave" -> GRAVE_ROOM;
            case "boundary", "milestone" -> STONE_ROOM;
            case "graffiti" -> SCRATCH_ROOM;
            case "shop_sign", "street_name", "road_sign" -> BOARD_ROOM;
            default -> WALL_ROOM;
        };
    }

    /** The lines that fit, in order. Never more than {@link #room()} of them. */
    public List<String> carved() {
        return lines.subList(0, Math.min(room(), lines.size()));
    }

    /** How many lines the surface could not hold. Said out loud rather than quietly dropped. */
    public int leftOff() {
        return Math.max(0, lines.size() - room());
    }

    /**
     * The object as a reader gets it: what fits, and then one honest line about the rest. A gravestone that
     * silently stopped mid-sentence would be a bug that reads like a broken object rather than a small stone.
     */
    public List<String> read() {
        List<String> out = new ArrayList<>(carved());
        if (leftOff() > 0) {
            out.add("There is no room on " + article() + " for the other " + leftOff()
                    + " line" + (leftOff() == 1 ? "" : "s") + ", so this is not all of it. What is left off is not "
                    + "on the stone.");
        }
        return out;
    }

    /** "the edict cut in the rock", with no article doubled up and none missing. */
    private String article() {
        String n = surface.name();
        return n.startsWith("a ") || n.startsWith("an ") || n.startsWith("the ") ? n : "the " + n;
    }

    public String asText() {
        return String.join("\n", read());
    }

    /** Who can read it, which is the surface's own demand and not a guess about the local rate. */
    public String readers() {
        String head = Character.toUpperCase(article().charAt(0)) + article().substring(1);
        return head + " is worth putting up only where people can read it: this one is written for "
                + "a place where more than " + Math.round(surface.literacy() * 100) + " in 100 can"
                + (author.isBlank() ? "" : ", and " + author + " had it put there") + ".";
    }

    // ---------------------------------------------------------------------------------------- fitting it on

    /** True when the surface holds everything the story has. */
    public boolean whole() {
        return leftOff() == 0;
    }

    /**
     * Break one line to a width, on spaces, without losing a word and without breaking a word.
     *
     * <p>A word longer than the width goes on a line of its own, over the width, because the alternative is
     * cutting it in half: a place called Thiruvananthapuram does not become Thiruvananthapur.
     */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int w = Math.max(1, width);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= w) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Every wrapped line of the object, in order, before pagination. */
    public List<String> wrapped(int width) {
        List<String> out = new ArrayList<>();
        for (String l : read()) {
            out.addAll(wrap(l, width));
        }
        return out;
    }

    /**
     * The object as pages, for a book or a screen that shows a fixed number of lines at a time. <b>Nothing is
     * dropped:</b> every wrapped line lands on exactly one page, and a page count that did not add up would be a
     * reader losing sentences.
     */
    public List<List<String>> pages(int width, int linesPerPage) {
        List<String> all = wrapped(width);
        List<List<String>> out = new ArrayList<>();
        int per = Math.max(1, linesPerPage);
        for (int i = 0; i < all.size(); i += per) {
            out.add(List.copyOf(all.subList(i, Math.min(all.size(), i + per))));
        }
        return out;
    }

    /** One page, or an empty list where there is no such page. A reader asking for page 9 of 3 gets nothing. */
    public List<String> page(int index, int width, int linesPerPage) {
        List<List<String>> p = pages(width, linesPerPage);
        return index < 0 || index >= p.size() ? List.of() : p.get(index);
    }

    /** One line, for a screen, saying what this object is and what it rests on. */
    public String ruleLine() {
        String head = Character.toUpperCase(article().charAt(0)) + article().substring(1);
        return String.format(Locale.ROOT, "%s at %s in %d, in %s: %d line%s of the record%s.",
                head, placeThen, year, script == null ? "the script this place writes in" : script,
                lines.size(), lines.size() == 1 ? "" : "s",
                leftOff() == 0 ? ", all of it on the surface"
                        : ", of which " + leftOff() + " could not be cut into it");
    }
}
