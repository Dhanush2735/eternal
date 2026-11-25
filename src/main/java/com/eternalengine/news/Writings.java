package com.eternalengine.news;

import com.eternalengine.civilization.CultureAtlas;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.Signs;
import com.eternalengine.technology.TechnologyAtlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 43 part 5: a letter and an inscription, as the things they were where and when you find
 * them.
 *
 * <p>The history book was already bought in thirteen forms by place and year (step 22,
 * {@link BookForms}), and the map and the newspaper are their own items. What the plan asked for
 * and the mod did not have was the two things writing is mostly made of: <b>a letter you send</b>
 * and <b>an inscription that stays where it was cut</b>. This class is those two, and it derives
 * every part of them from registers that already ship — so there is no table of letters here to
 * fall out of date.
 *
 * <table>
 * <caption>Where each part comes from</caption>
 * <tr><td>what it is made of</td><td>{@link BookForms} — papyrus, parchment, paper, palm leaf,
 *     clay, wax, bamboo, birch bark; per place and year</td></tr>
 * <tr><td>what it is written in</td><td>{@link Signs} — the same script lookup J3 uses, shared and
 *     not built twice</td></tr>
 * <tr><td>what it is written with</td><td>the material first (a reed stylus in clay, a brush on
 *     bamboo), then the pen technologies: fountain pens 1884, ballpoints 1945</td></tr>
 * <tr><td>how it travels</td><td>{@link NewsTravel} over the physical modes only — a letter is an
 *     object and cannot go down a wire</td></tr>
 * <tr><td>whether it can be sent at all</td><td>the <b>public post</b>, 1490 in Germany and later
 *     everywhere else, per country and dated</td></tr>
 * <tr><td>who can read it</td><td>{@link CultureAtlas#literacy}</td></tr>
 * </table>
 *
 * <p><b>The one fact that changes everything</b> is the post. Before there was a post you did not
 * send a letter; you gave it to somebody who happened to be going that way, and you found out
 * whether it had arrived when somebody came back. A ruler or a great house kept its own riders, and
 * this class does not model them — for everyone else, before the post, the only channel is the
 * incidental one, and that is what {@link #letter} returns.
 */
public final class Writings {

    /**
     * What a letter of this material is <i>of</i>, in English. The form's own title is a shop name
     * ("Notebook", "Parchment book") and does not fit the sentence: "a letter of notebook" is not
     * English, and "a letter of parchment book" is worse.
     */
    public static String sheet(BookForms.Form f) {
        return switch (f) {
            case NOTEBOOK -> "a sheet of paper";
            case PARCHMENT -> "a sheet of parchment";
            case PALM_LEAF -> "a palm leaf";
            case BIRCH_BARK -> "a piece of birch bark";
            case BARK_PAPER -> "a strip of bark paper";
            case PAPYRUS -> "a sheet of papyrus";
            case BAMBOO_SLIPS -> "a bundle of bamboo slips";
            case WAX_TABLETS -> "a pair of wax tablets";
            case CLAY_TABLETS -> "a clay tablet";
            case RUNE_STICKS -> "a rune stick";
            case KNOTTED_CORDS -> "a set of knotted cords";
            case MESSAGE_STICK -> "a message stick";
            case TALLY_STICK -> "a tally stick";
        };
    }

    /**
     * What a letter is written with. The material decides most of it: you press clay with a reed
     * and you scratch palm leaf with an iron point. Only on paper do the pen technologies matter.
     */
    public enum Instrument {
        REED_STYLUS("a reed stylus", "pressed into the wet clay"),
        WAX_STYLUS("a stylus of bone or metal", "scratched into the wax"),
        IRON_STYLUS("an iron stylus", "scratched in, then rubbed black"),
        BRUSH("a brush", "brushed on in columns"),
        REED_PEN("a reed pen", "written in ink of soot and gum"),
        QUILL("a pen cut from a quill", "written in ink of oak galls"),
        FOUNTAIN_PEN("a fountain pen", "written with ink from its own barrel"),
        BALLPOINT("a ballpoint pen", "written in paste ink");

        public final String name;
        public final String how;

        Instrument(String name, String how) {
            this.name = name;
            this.how = how;
        }
    }

    /**
     * Modes that can carry an object. The telegraph and the radio are deliberately not among them:
     * a letter cannot go down a wire, and {@code NewsTravel.fastest} would happily say it did.
     */
    static final List<NewsTravel.Mode> PHYSICAL = List.of(
            NewsTravel.Mode.TRAVELLER, NewsTravel.Mode.FOOT, NewsTravel.Mode.RIDER,
            NewsTravel.Mode.SAIL, NewsTravel.Mode.POST, NewsTravel.Mode.STEAMSHIP,
            NewsTravel.Mode.RAILWAY, NewsTravel.Mode.MOTOR);

    /**
     * The kinds of sign that are cut into something and left there, rather than hung up or nailed
     * to a door. These come from {@link Signs}, and they are the inscription half of this class.
     */
    static final List<String> CARVED = List.of(
            "edict", "boundary", "temple_inscription", "grave", "milestone", "graffiti");

    /**
     * The year of the first adhesive postage stamp, the Penny Black. Before it a letter was paid
     * for by whoever received it, and an envelope was a hand-folded thing; after it the gummed
     * envelope became general. This is the one date here that is not read from a register.
     */
    public static final int STAMP_YEAR = 1840;

    /** Below this share of readers, a letter is read aloud to whoever it is for. */
    public static final double READ_ALOUD = 0.5d;

    /**
     * Days a letter spends not moving: collected, sorted, carried to the office at the far end and
     * delivered. Only the post does this — a letter given to a traveller moves when the traveller
     * moves. Without it, London to Manchester in 1850 arrives "within the hour", because the
     * railway covers the distance in a morning and a letter is not a train.
     */
    public static final double HANDLING_DAYS = 1.0d;

    /**
     * A letter from one place to another in one year.
     *
     * @param material what it is written on
     * @param script   what it is written in, or null where the mod cannot name the script
     * @param with     what it is written with
     * @param closure  how it is closed
     * @param byPost   whether the public post has reached both ends
     * @param leg      how it goes and how long it takes
     * @param days     how long, in days
     * @param readers  the share of people at the far end who could read it themselves, 0 to 1
     * @param aloud    true where it would be read aloud rather than read
     */
    public record Letter(double fromLat, double fromLon, double toLat, double toLon, int year,
                         SettlementRank rank, BookForms.Form material, String script,
                         Instrument with, String closure, boolean byPost,
                         NewsTravel.Mode mode, double days, double readers, boolean aloud) {

        /** "by post", "with a traveller", "by railway". */
        public String carrier() {
            return mode == null ? "" : mode.phrase();
        }

        /** How long, in words: "in about nine days", "the next day". */
        public String when() {
            return NewsTravel.phrase(days);
        }

        /** What it is, as one paragraph. */
        public String line() {
            StringBuilder sb = new StringBuilder();
            sb.append("A letter of ").append(Writings.sheet(material));
            if (script != null && !script.isBlank()) {
                sb.append(", written in ").append(script);
            }
            sb.append(" with ").append(with.name).append(" — ").append(with.how).append(".");
            sb.append(" It is ").append(closure).append(".");
            if (byPost) {
                sb.append(" The post takes it ").append(carrier()).append(", and it arrives ")
                        .append(when()).append(".");
            } else {
                sb.append(" There is no post here in ").append(year)
                        .append(": it goes with whoever is going that way, and arrives ")
                        .append(when()).append(", if it arrives.");
            }
            sb.append(readers <= 0.0d
                    ? " Nobody at the far end can read it; it will be read aloud, or not at all."
                    : aloud
                    ? String.format(Locale.ROOT,
                    " Hardly anyone at the far end can read — about %.0f%% — so it will be read aloud to whoever it is for.",
                    readers * 100.0d)
                    : String.format(Locale.ROOT,
                    " About %.0f%% of people at the far end could read it themselves.",
                    readers * 100.0d));
            return sb.toString();
        }
    }

    /**
     * An inscription standing at a place in a year.
     *
     * @param script  what it is cut in, or null where the mod cannot name the script
     * @param surface what it is cut into
     * @param cut     how the letters are made
     * @param readers the share of people here who could read it, 0 to 1
     * @param kinds   the carved kinds standing here, from {@link Signs}
     */
    public record Inscription(double lat, double lon, int year, SettlementRank rank, String script,
                              String surface, String cut, double readers, List<Signs.Sign> kinds) {

        /** True where so few can read that the inscription is a claim and not a message. */
        public boolean claim() {
            return readers < 0.1d;
        }

        /** What is standing here, as one paragraph. */
        public String line() {
            // Where nobody writes, nothing is cut. Saying "something is cut into stone here" at a
            // place with no writing would invent an inscription that was never made.
            if (kinds.isEmpty()) {
                return "Nothing is cut here that can be read: what is known is remembered and told.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(script == null || script.isBlank()
                    ? "Something is cut into " + surface + " here"
                    : "What is cut into " + surface + " here is written in " + script);
            sb.append(", ").append(cut).append(".");
            for (Signs.Sign s : kinds) {
                String l = s.line();
                if (l == null || l.isBlank()) {
                    continue;
                }
                l = l.trim();
                // the table's lines are written without a capital so they can be joined; the first
                // one in a sentence has to have one
                l = Character.toUpperCase(l.charAt(0)) + l.substring(1);
                sb.append(" ").append(l);
                if (!l.endsWith(".")) {
                    sb.append(".");
                }
            }
            sb.append(claim()
                    ? " Almost nobody here can read it. It is not a message: it is a claim, put where" +
                    " nobody can take it down."
                    : String.format(Locale.ROOT,
                    " About %.0f%% of people here could read what it says.", readers * 100.0d));
            return sb.toString();
        }
    }

    private Writings() {
    }

    // ------------------------------------------------------------------ what you write with

    /** Has this technology reached this place by this year? */
    static boolean has(String techId, double lat, double lon, int year) {
        TechnologyAtlas atlas = TechnologyAtlas.get();
        TechnologyAtlas.Tech t = atlas.tech(techId);
        return t != null && atlas.arrivalAt(t, lat, lon) <= year;
    }

    /**
     * What this material is written with here and now.
     *
     * <p>The <b>script comes first</b>, because some signs cannot be made with the wrong tool:
     * Chinese characters are brushed, on paper as much as on bamboo, and cuneiform is pressed with
     * a reed. Then the material: you scratch palm leaf with an iron point and you write papyrus
     * with a reed pen. The pen technologies apply to paper only — nobody writes on a palm leaf with
     * a ballpoint.
     */
    public static Instrument instrument(BookForms.Form material, String script, double lat,
                                        double lon, int year) {
        if (script != null && !script.isBlank()) {
            String s = script.toLowerCase(Locale.ROOT);
            if (s.contains("chinese") || s.contains("clerical") || s.contains("oracle-bone")
                    || s.contains("maya")) {
                return Instrument.BRUSH;
            }
            if (s.contains("cuneiform")) {
                return Instrument.REED_STYLUS;
            }
        }
        if (material == BookForms.Form.NOTEBOOK) {
            if (has("ballpoint_pen", lat, lon, year)) {
                return Instrument.BALLPOINT;
            }
            if (has("fountain_pen", lat, lon, year)) {
                return Instrument.FOUNTAIN_PEN;
            }
        }
        return switch (material) {
            case CLAY_TABLETS -> Instrument.REED_STYLUS;
            case WAX_TABLETS -> Instrument.WAX_STYLUS;
            case PALM_LEAF -> Instrument.IRON_STYLUS;
            case BAMBOO_SLIPS, BARK_PAPER -> Instrument.BRUSH;
            case PAPYRUS -> Instrument.REED_PEN;
            default -> Instrument.QUILL;
        };
    }

    /** How a letter of this material is closed here and now. */
    public static String closure(BookForms.Form material, int year, boolean byPost) {
        return switch (material) {
            case CLAY_TABLETS ->
                    "an envelope of clay, with the same seal rolled across the join";
            case WAX_TABLETS ->
                    "the two boards tied shut with a cord, and wax pressed over the knot";
            case PAPYRUS ->
                    "rolled, tied with thread, and sealed with a lump of clay stamped from a ring";
            default -> (byPost && year >= STAMP_YEAR)
                    ? "folded into a gummed envelope and stamped"
                    : "folded, tied with thread, and closed with a seal in wax stamped from a signet ring";
        };
    }

    // ------------------------------------------------------------------ letters

    /** Has the public post reached this place by this year? */
    public static boolean postHere(double lat, double lon, int year) {
        return NewsTravel.available(NewsTravel.Mode.POST, lat, lon, year);
    }

    /**
     * The quickest way an object could go. Unlike {@link NewsTravel#fastest} this refuses the
     * telegraph and the radio, because a letter is a thing and a thing has to travel.
     */
    public static NewsTravel.Leg carry(int year, double la1, double lo1, double la2, double lo2,
                                       boolean sea) {
        NewsTravel.Leg best = null;
        for (NewsTravel.Mode m : PHYSICAL) {
            NewsTravel.Leg l = NewsTravel.leg(m, year, la1, lo1, la2, lo2, sea).orElse(null);
            if (l != null && (best == null || l.days() < best.days())) {
                best = l;
            }
        }
        return best;
    }

    /**
     * A letter sent from one place to another in this year: what it is made of, what it is written
     * in and with, how it is closed, how it goes, how long it takes, and whether anyone at the far
     * end can read it.
     */
    public static Letter letter(double fromLat, double fromLon, double toLat, double toLon,
                                int year, SettlementRank rank) {
        return letter(fromLat, fromLon, toLat, toLon, year, rank, false);
    }

    /**
     * @param sea true for a sea route, which is longer than the great-circle distance
     */
    public static Letter letter(double fromLat, double fromLon, double toLat, double toLon,
                                int year, SettlementRank rank, boolean sea) {
        BookForms.Form material = BookForms.best(fromLat, fromLon, year);
        String script = Signs.get().scriptAt(fromLat, fromLon, year);
        Instrument with = instrument(material, script, fromLat, fromLon, year);

        boolean byPost = postHere(fromLat, fromLon, year) && postHere(toLat, toLon, year);
        NewsTravel.Leg leg = byPost
                ? carry(year, fromLat, fromLon, toLat, toLon, sea)
                : NewsTravel.traveller(year, fromLat, fromLon, toLat, toLon, sea);
        double days = (leg == null ? 0.0d : leg.days()) + (byPost ? HANDLING_DAYS : 0.0d);
        NewsTravel.Mode mode = leg == null ? NewsTravel.Mode.TRAVELLER : leg.mode();

        double[] lit = CultureAtlas.get().literacy(toLat, toLon, year, rank);
        double readers = lit == null ? 0.0d : lit[0];

        return new Letter(fromLat, fromLon, toLat, toLon, year, rank, material, script, with,
                closure(material, year, byPost), byPost, mode, days, readers, readers < READ_ALOUD);
    }

    // ------------------------------------------------------------------ inscriptions

    /**
     * What an inscription is cut into here: clay where clay is what people write on, otherwise
     * stone.
     *
     * <p>Stone is nearly all of it, and that is the honest answer. <b>Bronze is deliberately not
     * modelled</b>: cast inscriptions are real and important — the Roman bronze tablet, the Shang
     * ritual vessel — but nothing in any register says which monument was cast and which was
     * carved, and every rule tried here that put bronze in ancient Rome also put it in Paris in
     * 1900. A guess that is right twice and wrong everywhere else is worse than the plain answer.
     */
    public static String surface(double lat, double lon, int year, SettlementRank rank) {
        List<BookForms.Form> forms = BookForms.writing(lat, lon, year);
        if (!forms.isEmpty() && forms.get(0) == BookForms.Form.CLAY_TABLETS) {
            return "clay";
        }
        return "stone";
    }

    /** How the letters are made, which follows what they are cut into. */
    public static String cut(String surface) {
        return switch (surface) {
            case "clay" -> "pressed in with a reed while it is still wet";
            default -> "cut with a chisel and a hammer, then painted red";
        };
    }

    /** The carved kinds of sign standing here, in the order they would be met. */
    public static List<Signs.Sign> carved(double lat, double lon, int year, SettlementRank rank) {
        Signs.Here here = Signs.get().hereAt(lat, lon, year, rank);
        List<Signs.Sign> out = new ArrayList<>();
        if (!here.writing()) {
            return out;
        }
        for (Signs.Sign s : here.signs()) {
            if (CARVED.contains(s.id())) {
                out.add(s);
            }
        }
        return out;
    }

    /** What is standing here, cut into the stone, in this year. */
    public static Inscription inscription(double lat, double lon, int year, SettlementRank rank) {
        String surface = surface(lat, lon, year, rank);
        double[] lit = CultureAtlas.get().literacy(lat, lon, year, rank);
        double readers = lit == null ? 0.0d : lit[0];
        return new Inscription(lat, lon, year, rank, Signs.get().scriptAt(lat, lon, year),
                surface, cut(surface), readers, carved(lat, lon, year, rank));
    }
}
