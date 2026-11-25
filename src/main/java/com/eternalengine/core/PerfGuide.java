package com.eternalengine.core;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>A hardware and settings guide for players, computed from measured numbers.</b> I10.
 *
 * <h2>Why a class instead of a blog post</h2>
 *
 * The row says "a hardware and settings guide for players (memory, view distance)". A guide is only
 * as good as its numbers, so the numbers live here, each one naming <b>its source</b>:
 *
 * <ul>
 *   <li><b>measured:</b> what this mod actually costs. Step 26 measured the data registers at 128 MB.
 *       Step 57's walk measured the loads (1,388 ms the gazetteer, 1,201 ms the ruler cache) and the
 *       lookups (0.009-0.08 ms warm, worst 0.24 ms against a 2 ms query budget).</li>
 *   <li><b>game default:</b> this mod's own budgets, shipped in the config: 64 MB of caches, a 10 ms
 *       tick budget, packets capped at 20 a second and 200,000 bytes a second.</li>
 *   <li><b>computed:</b> arithmetic over the two above. The memory floor is a sum, not a feeling.</li>
 *   <li><b>Minecraft's own:</b> the engine's limits: a custom payload over 32,767 bytes never arrives;
 *       a client draws at most 32 chunks.</li>
 *   <li><b>this mod's default:</b> the shipped settings, each with its config range.</li>
 * </ul>
 *
 * <p>The player document ({@code eternal/HARDWARE_GUIDE.md}) is written from these numbers and the
 * probe checks the two agree.
 */
public final class PerfGuide {

    /** <b>Where a number comes from.</b> A tip that does not name its source is not a tip. */
    public enum Source {
        MEASURED("measured"),
        GAME_DEFAULT("game default"),
        COMPUTED("computed from measured numbers"),
        MINECRAFT_OWN("Minecraft's own"),
        THIS_MODS_DEFAULT("this mod's default");

        private final String words;

        Source(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>One recommendation.</b> */
    public record Tip(String text, Source source) {

        public Tip {
            text = text == null ? "" : text.trim();
        }

        public String line() {
            return text + " [source: " + source.words() + "]";
        }
    }

    /** <b>The answer to one player.</b> {@code refused} is empty when the values are sane. */
    public record Advice(List<String> lines, String refused) {

        public Advice {
            lines = List.copyOf(lines == null ? List.of() : lines);
            refused = refused == null ? "" : refused.trim();
        }

        public boolean ok() {
            return refused.isEmpty();
        }
    }

    /** What Minecraft itself is given as a floor before any mod, in MB. */
    public static final int MINECRAFT_BASELINE_MB = 2048;

    /** The measured registers (step 26), in MB. */
    public static final int REGISTERS_MB = 128;

    /** The cache budget (game default, this mod's config), in MB. */
    public static final int CACHES_MB = 64;

    /** Headroom for the rest of the pack and the JVM (computed), in MB. */
    public static final int HEADROOM_MB = 256;

    /** Past the floor, the comfortable line adds this (computed), in MB. */
    public static final int COMFORT_EXTRA_MB = 1024;

    /** Minecraft's own client draw limit, in chunks. */
    public static final int MINECRAFT_MAX_VIEW_CHUNKS = 32;

    private PerfGuide() {
    }

    /** <b>The memory floor, computed as a sum, in MB.</b> */
    public static int floorMb() {
        return MINECRAFT_BASELINE_MB + REGISTERS_MB + CACHES_MB + HEADROOM_MB;
    }

    /** <b>The comfortable allocation, computed.</b> */
    public static int comfortableMb() {
        return floorMb() + COMFORT_EXTRA_MB;
    }

    /**
     * <b>The recommended view distance.</b> 8 chunks is 128 blocks, which is exactly this mod's
     * numbers-only line (step 57's distances): past it people are counts, and the client draws less.
     */
    public static int recommendedViewChunks() {
        return 8;
    }

    /** The shipped presence settings and their ranges, from the config. */
    public static List<Tip> settings() {
        List<Tip> out = new ArrayList<>();
        out.add(new Tip("presenceDrawnCrowd: true (figures stand around the living few; each figure "
                + "costs a draw call but no tick)", Source.THIS_MODS_DEFAULT));
        out.add(new Tip("presenceDrawnFiguresMax: 400 a district (25 to 600). This is a client-side "
                + "budget: figures are sent to your client and drawn", Source.THIS_MODS_DEFAULT));
        out.add(new Tip("presenceCrowdCeiling: 40 people in one place (8 to 64). The config's own "
                + "words: measure with /debug before raising it", Source.THIS_MODS_DEFAULT));
        out.add(new Tip("presenceCrowdConcentration: 40 (0 to 400). Higher gathers the crowd and "
                + "costs the client more to draw", Source.THIS_MODS_DEFAULT));
        out.add(new Tip("presenceNameTagBlocks: 24 (4 to 128). Nameplates within 24 blocks only",
                Source.THIS_MODS_DEFAULT));
        out.add(new Tip("maxTickBudgetMs: 10 (1 to 50). The measured walk spends well under 0.1 ms a "
                + "lookup, so 10 ms is a whole crowd's headroom", Source.GAME_DEFAULT));
        out.add(new Tip("A custom payload over 32,767 bytes never arrives: it is not slow, it is refused",
                Source.MINECRAFT_OWN));
        return out;
    }

    /**
     * <b>Advice for one player's machine.</b> Refuses nonsense (a check in that order: no memory
     * first, then a view distance below none, then one past Minecraft's own 32), and names the
     * shortfall when the memory is under the floor without refusing: the game may start, but the
     * caches and the registers will be fighting.
     */
    public static Advice advise(int memoryMb, int viewChunks) {
        if (memoryMb <= 0) {
            return new Advice(List.of(), "a machine given no memory runs nothing: the floor is "
                    + floorMb() + " MB (computed: " + MINECRAFT_BASELINE_MB + " for Minecraft itself + "
                    + REGISTERS_MB + " measured registers + " + CACHES_MB + " cache budget + "
                    + HEADROOM_MB + " headroom)");
        }
        if (viewChunks < 0) {
            return new Advice(List.of(), "a view distance below none is no view distance at all");
        }
        if (viewChunks > MINECRAFT_MAX_VIEW_CHUNKS) {
            return new Advice(List.of(), "Minecraft's own client draws at most "
                    + MINECRAFT_MAX_VIEW_CHUNKS + " chunks (" + viewChunks
                    + " is a wish, not a setting)");
        }
        List<String> lines = new ArrayList<>();
        if (memoryMb < floorMb()) {
            lines.add(memoryMb + " MB is below the floor of " + floorMb()
                    + " MB (computed: " + MINECRAFT_BASELINE_MB + " + " + REGISTERS_MB + " + "
                    + CACHES_MB + " + " + HEADROOM_MB + "): it may start, but the caches and the "
                    + "registers will be fighting. Allocate " + floorMb() + " MB or more"
                    + " [source: " + Source.COMPUTED.words() + "]");
        } else if (memoryMb < comfortableMb()) {
            lines.add(memoryMb + " MB clears the floor of " + floorMb()
                    + " MB and is workable; " + comfortableMb() + " MB is the comfortable line"
                    + " [source: " + Source.COMPUTED.words() + "]");
        } else {
            lines.add(memoryMb + " MB covers the comfortable line of " + comfortableMb()
                    + " MB (" + floorMb() + " computed floor + " + COMFORT_EXTRA_MB
                    + " extra): the caches stay inside their " + CACHES_MB + " MB budget and never "
                    + "need to trim [source: " + Source.COMPUTED.words() + "]");
        }
        int blocks = viewChunks * 16;
        if (viewChunks <= 4) {
            lines.add(viewChunks + " chunks (" + blocks + " blocks): close work; the draw stays at "
                    + "full detail inside 24 blocks and thins at 64 blocks [source: "
                    + Source.THIS_MODS_DEFAULT.words() + "]");
        } else if (viewChunks <= 8) {
            lines.add(viewChunks + " chunks (" + blocks + " blocks): the crowd is drawn near and "
                    + "reduced at 64 blocks; people past 128 blocks are counts only [source: "
                    + Source.THIS_MODS_DEFAULT.words() + "]");
        } else {
            lines.add(viewChunks + " chunks (" + blocks + " blocks): past 10 chunks this mod hides "
                    + "people at 160 blocks or the client's own view, whichever is nearer: the "
                    + "client's view distance is the last word [source: "
                    + Source.THIS_MODS_DEFAULT.words() + "]");
        }
        if (viewChunks == recommendedViewChunks()) {
            lines.add("8 chunks is the recommended line: 128 blocks is exactly where people become "
                    + "counts, so the machine draws the living few and counts the rest [source: "
                    + Source.THIS_MODS_DEFAULT.words() + "]");
        }
        lines.add("the measured walk: loads run 0.15-1,388 ms once and then lookups cost 0.009-0.08 ms; "
                + "if the game stutters, measure with Spark before you change anything [source: "
                + Source.MEASURED.words() + "]");
        return new Advice(lines, "");
    }

    public static String ruleLine() {
        return "Every recommendation names its source: measured numbers, game defaults, computed "
                + "arithmetic, or Minecraft's own limits. " + floorMb() + " MB is the computed floor; "
                + comfortableMb() + " MB is comfortable; 8 chunks (128 blocks) is the recommended "
                + "view distance, which is exactly where people become counts.";
    }
}
