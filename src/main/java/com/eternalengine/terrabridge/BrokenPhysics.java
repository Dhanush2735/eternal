package com.eternalengine.terrabridge;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Broken physics within Minecraft.</b> H5.
 *
 * <h2>Why this class exists</h2>
 *
 * H5 lists nine ways a ruptured universe can be physically wrong: gravity ({@code forge:entity_gravity}), a
 * fixed or racing sun, light, water that boils away (ultrawarm), exploding beds, extreme weather, strange
 * mobs, floating blocks. The row says what the resources are: <b>the Forge attribute; dimension types</b>.
 * Reading the list against Minecraft's real machinery showed it is <b>two lists, not one</b>:
 *
 * <table>
 *   <tr><td>gravity</td><td>the Forge entity gravity attribute</td><td>Minecraft</td></tr>
 *   <tr><td>a fixed sun</td><td>the dimension type's {@code fixed_time}</td><td>Minecraft</td></tr>
 *   <tr><td>a racing sun</td><td>a shortened day (no dimension flag)</td><td>mod-side</td></tr>
 *   <tr><td>light</td><td>{@code ambient_light}, {@code has_skylight}, monster-spawn light</td><td>Minecraft</td></tr>
 *   <tr><td>water boils away</td><td>{@code ultrawarm}</td><td>Minecraft</td></tr>
 *   <tr><td>exploding beds</td><td>{@code bed_works} false</td><td>Minecraft</td></tr>
 *   <tr><td>extreme weather</td><td>weather rules (no dimension flag)</td><td>mod-side</td></tr>
 *   <tr><td>strange mobs</td><td>spawn rules and monster light</td><td>part both</td></tr>
 *   <tr><td>floating blocks</td><td><b>no vanilla flag exists</b></td><td>mod-side</td></tr>
 * </table>
 *
 * <p>So this class is a <b>spec whose every field names its mechanism</b>, checkable against what a world
 * reports, exactly like {@link LandSpec} checks the land. A class that set nine flags and said "broken physics
 * applied" would lie about three of them, and this one cannot: {@link #appliedBy} and every changed line say
 * Minecraft's attribute, a dimension-type flag, or a mod-side rule, in those words.
 *
 * <h2>The sun cannot be fixed and racing</h2>
 *
 * <p>A fixed sun does not move, so a day length is meaningless under it; a spec that asks for both is refused
 * rather than quietly choosing one.
 */
public final class BrokenPhysics {

    /** <b>Who makes a field true.</b> The honest answer to "applied, by whom". */
    public enum Mechanism {
        MINECRAFT_ATTRIBUTE("Minecraft, via the Forge entity gravity attribute"),
        DIMENSION_TYPE("Minecraft, via the dimension type"),
        MOD_SIDE("mod-side rules (this mod must enforce it)"),
        PART_BOTH("part Minecraft, part mod-side");

        private final String words;

        Mechanism(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>One row of the table above.</b> A field, who enforces it, and by what. */
    public record Field(String name, Mechanism by, String how) {
        public String line() {
            return name + " · " + by.words() + " · " + how;
        }
    }

    /**
     * <b>How a universe's physics is wrong.</b> Every number is a scale of Earth's own: gravity 1.0 and day
     * length 1.0 are what the unbroken world has.
     */
    public record Spec(double gravity, Long fixedSunTime, double dayLengthScale, double ambientLight,
                       boolean ultrawarm, boolean bedsExplode, boolean extremeWeather, boolean strangeMobs,
                       boolean floatingBlocks, String why) {

        public String describe() {
            List<String> parts = new ArrayList<>();
            if (gravity != 1.0) {
                parts.add("gravity " + gravity + " (a scale of Earth's, " + Mechanism.MINECRAFT_ATTRIBUTE.words()
                        + ")");
            }
            if (fixedSunTime != null) {
                parts.add("a fixed sun at " + fixedSunTime + " (" + Mechanism.DIMENSION_TYPE.words()
                        + ", fixed_time)");
            } else if (dayLengthScale != 1.0) {
                parts.add("a racing sun: the day is " + dayLengthScale + " of Earth's (" + Mechanism.MOD_SIDE.words()
                        + ")");
            }
            if (ambientLight != 0.0) {
                parts.add("ambient light " + ambientLight + " (" + Mechanism.DIMENSION_TYPE.words() + ")");
            }
            if (ultrawarm) {
                parts.add("water boils away (" + Mechanism.DIMENSION_TYPE.words() + ", ultrawarm)");
            }
            if (bedsExplode) {
                parts.add("beds explode (" + Mechanism.DIMENSION_TYPE.words() + ", bed_works false)");
            }
            if (extremeWeather) {
                parts.add("extreme weather (" + Mechanism.MOD_SIDE.words() + ")");
            }
            if (strangeMobs) {
                parts.add("strange mobs (" + Mechanism.PART_BOTH.words() + ")");
            }
            if (floatingBlocks) {
                parts.add("floating blocks (" + Mechanism.MOD_SIDE.words() + ")");
            }
            return parts.isEmpty() ? "the physics of the unbroken world" : String.join("; ", parts);
        }
    }

    /** <b>What a world reports its physics to be.</b> The same facts, so a check can be a comparison. */
    public record Reported(double gravity, Long fixedSunTime, double dayLengthScale, double ambientLight,
                           boolean ultrawarm, boolean bedsExplode, boolean extremeWeather, boolean strangeMobs,
                           boolean floatingBlocks, String source) {
    }

    /** <b>One disagreement between the spec and the world.</b> */
    public record Difference(String what, String asked, String made, Mechanism by) {
        public String line() {
            return what + ": asked for " + asked + ", made " + made + " (" + by.words() + ")";
        }
    }

    /** <b>The outcome of making a spec.</b> Either one, or every reason there is none. */
    public record Made(Spec spec, List<String> refused) {
        public boolean ok() {
            return spec != null;
        }
    }

    private BrokenPhysics() {
    }

    /** The unbroken world: Earth's gravity, a moving sun, an ordinary day. */
    public static Spec earth() {
        return new Spec(1.0, null, 1.0, 0.0, false, false, false, false, false,
                "the physics of the unbroken world");
    }

    /**
     * <b>Make a spec, or say why not.</b> Gravity is a scale of Earth's and cannot be below none; the sun is
     * fixed or it races, never both; light is a share of full.
     */
    public static Made make(double gravity, Long fixedSunTime, double dayLengthScale, double ambientLight,
                            boolean ultrawarm, boolean bedsExplode, boolean extremeWeather,
                            boolean strangeMobs, boolean floatingBlocks, String why) {
        List<String> refused = new ArrayList<>();
        if (gravity < 0) {
            refused.add("gravity " + gravity + " is below no gravity at all, and the Forge attribute is a "
                    + "scale of Earth's: say what is meant");
        }
        if (fixedSunTime != null && (fixedSunTime < 0 || fixedSunTime >= 24000)) {
            refused.add("a fixed sun at " + fixedSunTime + " is not a time of Minecraft's day (0 to 23999)");
        }
        if (fixedSunTime != null && dayLengthScale != 1.0) {
            refused.add("a sun cannot be fixed and racing: a fixed sun does not move, so the day's length "
                    + "means nothing under it");
        }
        if (dayLengthScale <= 0) {
            refused.add("the day cannot be " + dayLengthScale + " of Earth's: a day that lasts nothing or "
                    + "less is no day");
        }
        if (ambientLight < 0 || ambientLight > 1) {
            refused.add("ambient light " + ambientLight + " is not a share of full light (0 to 1)");
        }
        if (!refused.isEmpty()) {
            return new Made(null, refused);
        }
        return new Made(new Spec(gravity, fixedSunTime, dayLengthScale, ambientLight, ultrawarm, bedsExplode,
                extremeWeather, strangeMobs, floatingBlocks, why == null ? "" : why.trim()), List.of());
    }

    /** <b>What is broken, in words that name the mechanism.</b> Empty for the unbroken world. */
    public static List<String> changesFromEarth(Spec s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        if (s.gravity() != 1.0) {
            out.add("gravity " + s.gravity() + " · " + Mechanism.MINECRAFT_ATTRIBUTE.words());
        }
        if (s.fixedSunTime() != null) {
            out.add("a fixed sun at " + s.fixedSunTime() + " · " + Mechanism.DIMENSION_TYPE.words()
                    + " (fixed_time)");
        } else if (s.dayLengthScale() != 1.0) {
            out.add("a racing sun: the day is " + s.dayLengthScale() + " of Earth's · "
                    + Mechanism.MOD_SIDE.words());
        }
        if (s.ambientLight() != 0.0) {
            out.add("ambient light " + s.ambientLight() + " · " + Mechanism.DIMENSION_TYPE.words());
        }
        if (s.ultrawarm()) {
            out.add("water boils away · " + Mechanism.DIMENSION_TYPE.words() + " (ultrawarm)");
        }
        if (s.bedsExplode()) {
            out.add("beds explode · " + Mechanism.DIMENSION_TYPE.words() + " (bed_works false)");
        }
        if (s.extremeWeather()) {
            out.add("extreme weather · " + Mechanism.MOD_SIDE.words());
        }
        if (s.strangeMobs()) {
            out.add("strange mobs · " + Mechanism.PART_BOTH.words());
        }
        if (s.floatingBlocks()) {
            out.add("floating blocks · " + Mechanism.MOD_SIDE.words());
        }
        return out;
    }

    /**
     * <b>What a spec expects each field to be enforced by.</b> The honest "applied, by whom": four of the
     * nine are mod-side or half mod-side, and this table is what stops anyone claiming Minecraft did them.
     */
    public static List<Field> fields() {
        List<Field> out = new ArrayList<>();
        out.add(new Field("gravity", Mechanism.MINECRAFT_ATTRIBUTE, "forge:entity_gravity, a scale of "
                + "Earth's on every entity"));
        out.add(new Field("a fixed sun", Mechanism.DIMENSION_TYPE, "fixed_time, one time of Minecraft's day "
                + "held forever"));
        out.add(new Field("a racing sun", Mechanism.MOD_SIDE, "a shortened day: the clock runs faster, and "
                + "no dimension flag does this"));
        out.add(new Field("light", Mechanism.DIMENSION_TYPE, "ambient_light and has_skylight"));
        out.add(new Field("water boils away", Mechanism.DIMENSION_TYPE, "ultrawarm: water cannot be placed, "
                + "lava runs far"));
        out.add(new Field("exploding beds", Mechanism.DIMENSION_TYPE, "bed_works false: beds explode instead "
                + "of setting the spawn"));
        out.add(new Field("extreme weather", Mechanism.MOD_SIDE, "weather rules: no dimension flag exists"));
        out.add(new Field("strange mobs", Mechanism.PART_BOTH, "monster_spawn_light_level sets who can "
                + "spawn; which mobs is mod-side"));
        out.add(new Field("floating blocks", Mechanism.MOD_SIDE, "no vanilla flag exists at all: blocks that "
                + "should fall do not"));
        return out;
    }

    /** The mechanism that enforces one named field, or null if there is no such field. */
    public static Mechanism appliedBy(String field) {
        for (Field f : fields()) {
            if (f.name().equalsIgnoreCase(field == null ? "" : field.trim())) {
                return f.by();
            }
        }
        return null;
    }

    /**
     * <b>Check a spec against what a world reports.</b> Key by key, naming every disagreement and the
     * mechanism each field belongs to. A world that reports nothing is not a pass.
     */
    public static List<Difference> diff(Spec asked, Reported made) {
        List<Difference> out = new ArrayList<>();
        if (asked == null) {
            out.add(new Difference("(the spec)", "a physics", "nothing", Mechanism.MOD_SIDE));
            return out;
        }
        if (made == null) {
            out.add(new Difference("(the report)", asked.describe(), "nothing was reported",
                    Mechanism.MOD_SIDE));
            return out;
        }
        if (asked.gravity() != made.gravity()) {
            out.add(new Difference("gravity", String.valueOf(asked.gravity()),
                    String.valueOf(made.gravity()), Mechanism.MINECRAFT_ATTRIBUTE));
        }
        if (asked.fixedSunTime() == null ? made.fixedSunTime() != null
                : !asked.fixedSunTime().equals(made.fixedSunTime())) {
            out.add(new Difference("the sun", asked.fixedSunTime() == null ? "a moving sun"
                    : "fixed at " + asked.fixedSunTime(), made.fixedSunTime() == null ? "a moving sun"
                    : "fixed at " + made.fixedSunTime(), Mechanism.DIMENSION_TYPE));
        }
        if (asked.dayLengthScale() != made.dayLengthScale()) {
            out.add(new Difference("the day's length", String.valueOf(asked.dayLengthScale()),
                    String.valueOf(made.dayLengthScale()), Mechanism.MOD_SIDE));
        }
        if (asked.ambientLight() != made.ambientLight()) {
            out.add(new Difference("the light", String.valueOf(asked.ambientLight()),
                    String.valueOf(made.ambientLight()), Mechanism.DIMENSION_TYPE));
        }
        if (asked.ultrawarm() != made.ultrawarm()) {
            out.add(new Difference("the water", asked.ultrawarm() ? "boiling" : "ordinary",
                    made.ultrawarm() ? "boiling" : "ordinary", Mechanism.DIMENSION_TYPE));
        }
        if (asked.bedsExplode() != made.bedsExplode()) {
            out.add(new Difference("the beds", asked.bedsExplode() ? "exploding" : "working",
                    made.bedsExplode() ? "exploding" : "working", Mechanism.DIMENSION_TYPE));
        }
        if (asked.extremeWeather() != made.extremeWeather()) {
            out.add(new Difference("the weather", asked.extremeWeather() ? "extreme" : "ordinary",
                    made.extremeWeather() ? "extreme" : "ordinary", Mechanism.MOD_SIDE));
        }
        if (asked.strangeMobs() != made.strangeMobs()) {
            out.add(new Difference("the mobs", asked.strangeMobs() ? "strange" : "ordinary",
                    made.strangeMobs() ? "strange" : "ordinary", Mechanism.PART_BOTH));
        }
        if (asked.floatingBlocks() != made.floatingBlocks()) {
            out.add(new Difference("the blocks", asked.floatingBlocks() ? "floating" : "falling",
                    made.floatingBlocks() ? "floating" : "falling", Mechanism.MOD_SIDE));
        }
        return out;
    }

    public static String ruleLine() {
        return "Broken physics is nine facts, and every one names who enforces it: a Minecraft attribute, a "
                + "dimension-type flag, or a mod-side rule. Four of the nine are mod-side or half so, and no "
                + "screen may say Minecraft applied those.";
    }
}
