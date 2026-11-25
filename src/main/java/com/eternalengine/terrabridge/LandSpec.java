package com.eternalengine.terrabridge;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The land of a universe: sea level, height, upside down, mirrored.</b> H3's land half.
 *
 * <h2>Why this class exists</h2>
 *
 * H3 says a rupture makes "a new universe with other land (sea level, height, upside down, mirrored)", and it
 * lives in Terra++ because Terra++ is the generator. The dependency is "check it". So this class holds
 * <b>intent</b> (what the land of this universe IS) and the words to <b>check it against what was
 * generated</b>, and it performs no generation of its own: {@code TerraBridge}'s standing rule is that this
 * mod does no projection math and no elevation classification, and that rule is not broken here.
 *
 * <h2>The four words and what they mean</h2>
 *
 * <ul>
 *   <li><b>Sea level</b>: the Y where water stands. Earth's is 63 (Terra++'s own fallback, which is the
 *       number this mod treats as "unbroken").</li>
 *   <li><b>Height</b>: the Y range of the world, min to max. Earth's is -2032 to 2032, again Terra++'s
 *       fallback.</li>
 *   <li><b>Upside down</b>: the land's height profile is inverted. What was high ground is low, what was the
 *       sea floor is the sky. A generator that maps depth to height is upside down.</li>
 *   <li><b>Mirrored</b>: one horizontal axis is flipped. It names WHICH axis ({@link Mirror}), because
 *       "mirrored" with no direction is not a land, it is a shrug.</li>
 * </ul>
 *
 * <h2>Checking</h2>
 *
 * <p>{@link #diff} compares a spec against a {@link Reported} (what the generator says it made) key by key and
 * names every disagreement - the same discipline as {@code BranchSave.diff}. A generator that says nothing is
 * not a pass; the caller's seam says it could not ask (see {@code Ruptures}, part 3).
 */
public final class LandSpec {

    /** Earth's own land, as Terra++ falls back to when it has nothing better: the unbroken spec. */
    public static final int EARTH_SEA = 63;
    public static final int EARTH_MIN_Y = -2032;
    public static final int EARTH_MAX_Y = 2032;

    /** Minecraft will not build a dimension taller than this. */
    public static final int MAX_HEIGHT = 4096;
    /** And none shorter than this is worth calling a world. */
    public static final int MIN_HEIGHT = 16;

    /** <b>Which way a mirrored world is flipped.</b> A mirror with no direction is not a land. */
    public enum Mirror {
        NONE("not mirrored"),
        EAST_WEST("mirrored east-west: what was east is west"),
        NORTH_SOUTH("mirrored north-south: what was north is south");

        private final String words;

        Mirror(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * <b>What a universe's land is.</b> Made once through {@link #make}, which validates; a land half-stated
     * is a land nobody can generate.
     */
    public record Spec(int seaLevel, int minY, int maxY, boolean upsideDown, Mirror mirror, String why) {

        public int height() {
            return maxY - minY + 1;
        }

        public String describe() {
            return "sea level " + seaLevel + " · " + minY + ".." + maxY
                    + (upsideDown ? " · upside down: the land's height profile is inverted" : "")
                    + (mirror == Mirror.NONE ? "" : " · " + mirror.words())
                    + (why == null || why.isEmpty() ? "" : " · " + why);
        }
    }

    /**
     * <b>What a generator says it made.</b> The same five facts in the same words, so a check can be a
     * comparison and not an argument.
     */
    public record Reported(int seaLevel, int minY, int maxY, boolean upsideDown, Mirror mirror,
                           String source) {
    }

    /** <b>One disagreement between what was asked and what was made.</b> */
    public record Difference(String what, String asked, String made) {
        public String line() {
            return what + ": asked for " + asked + ", made " + made;
        }
    }

    /** <b>The outcome of making a spec.</b> Either a spec, or every reason there is none. */
    public record Made(Spec spec, List<String> refused) {
        public boolean ok() {
            return spec != null;
        }
    }

    private LandSpec() {
    }

    /** The unbroken land: Earth, as Terra++'s own fallbacks describe it. */
    public static Spec earth() {
        return new Spec(EARTH_SEA, EARTH_MIN_Y, EARTH_MAX_Y, false, Mirror.NONE, "the Earth as Terra++ "
                + "makes it");
    }

    /**
     * <b>Make a spec, or say why not.</b> The rules are Minecraft's own constraints where it has them (a
     * dimension is at most 4096 tall) and plain sense where it does not (the sea must be somewhere inside the
     * world). Sixteen-alignment is NOT checked: a generator may ask for it and Terra++'s own Earth numbers do
     * not honour it, so refusing it here would refuse the unbroken land itself.
     */
    public static Made make(int seaLevel, int minY, int maxY, boolean upsideDown, Mirror mirror, String why) {
        List<String> refused = new ArrayList<>();
        if (minY >= maxY) {
            refused.add("the floor (" + minY + ") must be below the ceiling (" + maxY + ")");
        }
        int height = maxY - minY + 1;
        if (minY < maxY && (height < MIN_HEIGHT || height > MAX_HEIGHT)) {
            refused.add("a world " + height + " tall is not a world: Minecraft's dimensions run from "
                    + MIN_HEIGHT + " to " + MAX_HEIGHT + " blocks");
        }
        if (seaLevel < minY || seaLevel > maxY) {
            refused.add("the sea (" + seaLevel + ") is outside the world (" + minY + ".." + maxY
                    + "), and water cannot stand outside the world");
        }
        if (mirror == null) {
            refused.add("a land must say which way it is mirrored, or that it is not: "
                    + Mirror.NONE.words());
        }
        if (!refused.isEmpty()) {
            return new Made(null, refused);
        }
        return new Made(new Spec(seaLevel, minY, maxY, upsideDown, mirror, why == null ? "" : why.trim()),
                List.of());
    }

    /** <b>What differs from the unbroken land.</b> In words a reader can check one line at a time. */
    public static List<String> changesFromEarth(Spec s) {
        return changes(earth(), s);
    }

    /** <b>What differs between two lands.</b> A rupture's whole story: what it changed, and nothing else. */
    public static List<String> changes(Spec from, Spec to) {
        List<String> out = new ArrayList<>();
        if (from == null || to == null) {
            return out;
        }
        if (from.seaLevel() != to.seaLevel()) {
            out.add("sea level " + from.seaLevel() + " -> " + to.seaLevel());
        }
        if (from.minY() != to.minY() || from.maxY() != to.maxY()) {
            out.add("height " + from.minY() + ".." + from.maxY() + " -> " + to.minY() + ".." + to.maxY());
        }
        if (from.upsideDown() != to.upsideDown()) {
            out.add(to.upsideDown()
                    ? "upside down: the land's height profile is inverted"
                    : "right way up again");
        }
        if (from.mirror() != to.mirror()) {
            out.add(to.mirror() == Mirror.NONE ? "the mirror is gone" : to.mirror().words());
        }
        return out;
    }

    /**
     * <b>Check a spec against what was generated.</b> Key by key, naming every disagreement. An exact match
     * is an empty list; a generator that reports nothing at all is not this method's to forgive (the seam's
     * own absent-bridge says so).
     */
    public static List<Difference> diff(Spec asked, Reported made) {
        List<Difference> out = new ArrayList<>();
        if (asked == null) {
            out.add(new Difference("(the spec)", "a land", "nothing"));
            return out;
        }
        if (made == null) {
            out.add(new Difference("(the report)", asked.describe(), "nothing was reported"));
            return out;
        }
        if (asked.seaLevel() != made.seaLevel()) {
            out.add(new Difference("sea level", String.valueOf(asked.seaLevel()),
                    String.valueOf(made.seaLevel())));
        }
        if (asked.minY() != made.minY()) {
            out.add(new Difference("the floor", String.valueOf(asked.minY()), String.valueOf(made.minY())));
        }
        if (asked.maxY() != made.maxY()) {
            out.add(new Difference("the ceiling", String.valueOf(asked.maxY()),
                    String.valueOf(made.maxY())));
        }
        if (asked.upsideDown() != made.upsideDown()) {
            out.add(new Difference("orientation", asked.upsideDown() ? "upside down" : "right way up",
                    made.upsideDown() ? "upside down" : "right way up"));
        }
        if (asked.mirror() != made.mirror()) {
            out.add(new Difference("the mirror", asked.mirror().words(), made.mirror().words()));
        }
        return out;
    }

    public static String ruleLine() {
        return "A universe's land is five facts in plain words: the sea, the floor, the ceiling, whether it is "
                + "upside down, and which way it is mirrored. This mod states them and checks them; Terra++ "
                + "generates the land, and it does no projection math here.";
    }
}
