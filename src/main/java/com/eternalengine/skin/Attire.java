package com.eternalengine.skin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 47 part 4 (E21): <b>clothes the player can wear to blend in.</b>
 *
 * <p>The plan's line, from turn 95: <i>"clothes the player can wear to blend in (people react to
 * strange clothes)"</i>. Two things are needed and only one of them is a list of garments:
 *
 * <ol>
 *   <li><b>A norm</b> for each place and year, which {@link Wardrobe} now supplies.</li>
 *   <li><b>A judgement</b> of what a particular player is wearing against it, which is this class.
 *       </li>
 * </ol>
 *
 * <h2>The three things that make a person stand out, in the order they matter</h2>
 *
 * <ol>
 *   <li><b>An object from another time.</b> A player in 1300 holding an ender pearl is not
 *       unusually dressed, he is impossible, and {@code Anachronisms} already knows which items
 *       those are. This class asks it rather than keeping a second list.</li>
 *   <li><b>Metal where there is no metal.</b> Full plate in 800 BC is not a fashion error, it is a
 *       fortune and a technology that does not exist, and the reaction is not mockery: it is a lord
 *       deciding whether to rob him.</li>
 *   <li><b>The wrong cloth.</b> A modern synthetic jacket in a medieval market is the mildest of the
 *       three and the most comic.</li>
 * </ol>
 *
 * <h2>What a reaction is, and what it is not</h2>
 *
 * <p>This class <b>does not punish</b>. It returns a {@link Reading} with a level and a reason, and
 * the systems that already react to people - step 42's reputation, step 38's scenes, the villager
 * behaviour - decide what to do with it. A mod that fined a player for wearing trousers would be a
 * bad mod, and the note on each level says what the world is actually thinking.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Attire {

    /** How much a player's appearance departs from the local norm. */
    public enum Level {
        /** Nobody looks twice. */
        BLEND(0, "he is dressed like everybody else"),
        /** Notices, and concludes he is foreign rather than mad. */
        FOREIGN(1, "they will take him for a foreigner, which is ordinary and survivable"),
        /** Unmistakable: a stranger, and one worth talking about. */
        STRANGE(2, "people will look, and the story will get better each time it is told"),
        /** Impossible: not a foreigner but a thing from elsewhere. */
        IMPOSSIBLE(3, "an object of this kind does not exist in this year, and that is what they "
                + "will remember");

        private final int weight;
        private final String note;

        Level(int weight, String note) {
            this.weight = weight;
            this.note = note;
        }

        public int weight() {
            return weight;
        }

        public String note() {
            return note;
        }
    }

    /**
     * The answer.
     *
     * @param level  how far from the local norm
     * @param reason the one thing that most explains it
     * @param what   the local norm, as garments a person here would be wearing
     */
    public record Reading(Level level, String reason, List<String> what) {

        public boolean blends() {
            return level == Level.BLEND;
        }

        public String line() {
            return level.name().toLowerCase(Locale.ROOT) + ": " + reason + " ("
                    + level.note() + ")";
        }
    }

    /**
     * What is a TECHNOLOGY rather than a fashion.
     *
     * <p><b>Two questions, and the first version of this got them confused.</b> Iron is ancient: a
     * material from about 1200 BC, and an iron sword in AD 100 is ordinary. Iron <i>plate</i> is
     * not: a fitted chestplate needs a rolling mill and a tailor of steel, and it appears in the
     * 1350s. So a material's year and a form's year are decided separately and the later applies.
     *
     * <p><b>The form applies only to plate.</b> A leather or mail "chestplate" is a tunic or a coat
     * of rings, and mail is a thousand years older than plate. A shared word in an item id is not a
     * shared technology, which is the class of mistake this project keeps finding in other data.
     */
    private static final String[][] MATERIAL = {
            // { token in the id, the year it is plausible from, the name, does it block a form? }
            {"netherite", "9999", "netherite", "no"},
            {"elytra", "9999", "a pair of wings", "no"},
            {"diamond", "9999", "diamond", "no"},
            {"chainmail", "-400", "mail", "no"},
            {"iron", "-1200", "iron", "yes"},
            {"gold", "-3000", "gold", "no"},
            {"leather", "-3000", "leather", "no"},
    };

    /** The fitted forms, which only a metal that can be rolled into plate can take. */
    private static final String[][] FORM = {
            {"_chestplate", "1350", "plate armour"},
            {"_leggings", "1350", "plate leg armour"},
            {"_helmet", "1200", "a steel helmet"},
            {"_boots", "1200", "fitted boots of steel"},
    };

    /**
     * Walk the gear: the material first, then the form, and the later of the two wins.
     *
     * @return a reading when something is too early, or null when all of it is plausible
     */
    @Nullable
    private static Reading checkGear(List<String> worn, int year, List<String> norm) {
        for (String item : worn) {
            if (item == null) continue;
            String id = item.toLowerCase(Locale.ROOT);
            String[] mat = null;
            for (String[] m : MATERIAL) {
                if (id.contains(m[0])) { mat = m; break; }
            }
            if (mat == null) continue;
            if (year < Integer.parseInt(mat[1])) {
                return new Reading(Level.STRANGE, mat[2] + " is not available here yet, and a man "
                        + "wearing it is a man with a fortune and no explanation", norm);
            }
            if (!"yes".equals(mat[3])) continue;
            for (String[] f : FORM) {
                if (!id.contains(f[0])) continue;
                if (year < Integer.parseInt(f[1])) {
                    return new Reading(Level.STRANGE, f[2] + " is not available here yet, and a man "
                            + "wearing it is a man with a fortune and no explanation", norm);
                }
                break;
            }
        }
        return null;
    }

    /** The garments the modern wardrobe knows, which are the ones that read as "wrong era". */
    /**
     * Judge what a player is wearing here and now.
     *
     * @param worn     item ids, any order, armour and hands both; nulls are ignored
     * @param culture  the skin pool's culture for this place, or null when it has none
     * @param year     the year
     * @param anachronismCheck answers "is this object impossible here", which is
     *                         {@code Anachronisms.judge(...).allowed()} at the call site. Passed in
     *                         rather than imported, so this class keeps no Minecraft dependency and
     *                         the two registers can be tested apart.
     */
    public static Reading read(List<String> worn, @Nullable String culture, int year,
                               @Nullable AnachronismCheck anachronismCheck) {
        List<String> norm = culture == null ? List.of()
                : Wardrobe.get().outfitFor(culture, year, false, "citizen");

        if (worn == null || worn.isEmpty()) {
            // Naked in a dressed world is not a fashion statement either.
            return new Reading(norm.isEmpty() ? Level.BLEND : Level.STRANGE,
                    norm.isEmpty() ? "there is nothing to wear and he is not wearing it"
                                   : "he is wearing nothing at all",
                    norm);
        }

        // 1. THE IMPOSSIBLE, WHICH OUTRANKS EVERYTHING.
        if (anachronismCheck != null) {
            for (String item : worn) {
                if (item == null) continue;
                if (!anachronismCheck.allowed(item, year)) {
                    return new Reading(Level.IMPOSSIBLE,
                            item.toLowerCase(Locale.ROOT) + " does not exist in this year", norm);
                }
            }
        }

        // 2. THE FORM BEFORE THE MATERIAL.
        Reading gear = checkGear(worn, year, norm);
        if (gear != null) return gear;

        // 3. THE WRONG CLOTH: modern garments where the period wardrobe answers.
        if (!norm.isEmpty()) {
            Set<String> period = new LinkedHashSet<>(norm);
            for (String item : worn) {
                if (item == null) continue;
                String id = item.toLowerCase(Locale.ROOT);
                for (String modern : MODERN) {
                    if (id.contains(modern) && !period.contains(modern)) {
                        return new Reading(Level.FOREIGN,
                                "a " + modern.replace('_', ' ') + " is a garment of a later century",
                                norm);
                    }
                }
            }
        }

        return new Reading(Level.BLEND, Level.BLEND.note(), norm);
    }

    private static final List<String> MODERN = List.of(
            "crew_tee", "graphic_tee", "polo", "hoodie", "sweatshirt", "cardigan", "button_down",
            "trench", "bomber", "blazer", "jeans", "trousers", "cargo", "joggers", "sneakers",
            "chelsea", "tank", "varsity", "puffer");

    /** Answers whether an object may exist in a year. The wiring half passes {@code Anachronisms}. */
    @FunctionalInterface
    public interface AnachronismCheck {
        boolean allowed(String itemId, int year);
    }

    /**
     * What a player should put on to blend in, as a list of garment names for a culture and year.
     *
     * <p>E21's actual deliverable: not a judgement but an <b>answer</b>, so a player can ask "what
     * do people wear here" and be told.
     */
    public static List<String> toBlendIn(String culture, int year, boolean female) {
        List<String> outfit = Wardrobe.get().outfitFor(culture, year, female, "citizen");
        if (outfit.isEmpty()) {
            return List.of("this place in this year has no wardrobe the register knows, so nothing "
                    + "will make him look like a local");
        }
        return outfit;
    }

    /** The levels, for a command that lists them. */
    public static List<String> levels() {
        List<String> out = new ArrayList<>();
        for (Level l : Level.values()) out.add(l.weight() + " " + l.name().toLowerCase(Locale.ROOT)
                + ": " + l.note());
        return out;
    }
}
