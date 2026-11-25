package com.eternalengine.compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Performance mods for your pack, checked against what this mod actually does.</b> I9.
 *
 * <h2>Why this class exists</h2>
 *
 * I9 names six mods - Spark, ModernFix, FerriteCore, Entity Culling, Embeddium, Canary - and says
 * <b>checked to work with Terra++</b>. This environment cannot run the live pair: there is no
 * Minecraft here and no Terra++. So the honest check is split in two, and the class never blurs it:
 *
 * <ul>
 *   <li><b>checked here:</b> what each mod changes, and whether anything in this mod depends on it.
 *       This mod's data registers load through its own loaders, its crowds are vanilla entities with
 *       their AI off, its nameplates ride the normal pipeline, and its rebuild wait is a state, not a
 *       mixin. That much was read and is stated.</li>
 *   <li><b>not checked here:</b> the pair in a running game. Every verdict that needs it says CHECK_LIVE
 *       and names <b>the exact test to run</b>, because a verdict without a test is a hope.</li>
 * </ul>
 *
 * <h2>The order to pack in</h2>
 *
 * <p>{@link #pack()} is the recommended order and it starts with Spark: <b>measure before you tune</b>
 * (step 57's walk is this mod's own version of that rule).
 */
public final class PerformanceMods {

    /** <b>What a verdict means, in words.</b> */
    public enum Verdict {
        PACK_WITH_CONFIDENCE("pack with confidence",
                "nothing this mod does depends on what this performance mod changes"),
        CHECK_LIVE("check live before shipping the pack",
                "it changes a path this mod uses; run the named test in a live game");

        private final String words;
        private final String why;

        Verdict(String words, String why) {
            this.words = words;
            this.why = why;
        }

        public String words() {
            return words;
        }

        public String why() {
            return why;
        }
    }

    /**
     * <b>One mod.</b> What it does, what in this mod it touches, the verdict, its reason, and the live
     * test where one is owed.
     */
    public record Mod(String id, String name, String whatItDoes, String touches, Verdict verdict,
                      String reason, String liveTest) {

        public Mod {
            id = id == null ? "" : id.trim();
            name = name == null ? "" : name.trim();
            whatItDoes = whatItDoes == null ? "" : whatItDoes.trim();
            touches = touches == null ? "" : touches.trim();
            reason = reason == null ? "" : reason.trim();
            liveTest = liveTest == null ? "" : liveTest.trim();
        }

        public boolean checkedHere() {
            return verdict == Verdict.PACK_WITH_CONFIDENCE;
        }

        public String line() {
            return name + " (" + id + ") · " + verdict.words() + " · " + reason
                    + (liveTest.isEmpty() ? "" : " · live test: " + liveTest);
        }
    }

    private PerformanceMods() {
    }

    /** The six mods the plan names, each with its honest verdict. */
    public static List<Mod> mods() {
        List<Mod> out = new ArrayList<>();
        out.add(new Mod("spark", "Spark", "a profiler: it measures the game and changes nothing",
                "nothing: this mod's own walk is the offline version of what Spark does live",
                Verdict.PACK_WITH_CONFIDENCE,
                "a profiler that changes nothing cannot break nothing; it is also how the live "
                        + "30-minute walk gets measured",
                ""));
        out.add(new Mod("modernfix", "ModernFix", "startup and memory fixes for Forge itself",
                "the game's loading and model memory; this mod's registers load through its own "
                        + "loaders and are not model data",
                Verdict.PACK_WITH_CONFIDENCE,
                "checked here: nothing this mod does reads what ModernFix replaces; its memory work "
                        + "and ours (64 MB of caches, step 26's budget) do not overlap",
                ""));
        out.add(new Mod("ferritecore", "FerriteCore", "memory optimization for models and registries",
                "the game's model and registry memory; this mod's 128 MB of registers are its own",
                Verdict.PACK_WITH_CONFIDENCE,
                "checked here: the data registers are plain maps this mod loads itself; FerriteCore "
                        + "compresses what Minecraft stores, not what this mod stores",
                ""));
        out.add(new Mod("entityculling", "Entity Culling", "skips drawing entities that cannot be seen",
                "the drawn crowd: up to 400 figures a district (presenceDrawnFiguresMax), sent to the "
                        + "client and drawn",
                Verdict.CHECK_LIVE,
                "it decides what is drawn, and this mod's crowds are exactly drawn figures; hiding "
                        + "what cannot be seen is also step 57's own rule (I5), so the two should "
                        + "agree - but agreeing is a thing to watch, not a thing to assume",
                "stand in a packed square with it on and off: the crowd should thin only where "
                        + "buildings hide it, and never flicker at the edges"));
        out.add(new Mod("embeddium", "Embeddium", "a rendering overhaul (the Forge Sodium)",
                "everything drawn: figures, nameplates, the map overlays",
                Verdict.CHECK_LIVE,
                "it replaces the renderer this mod's figures and nameplates ride; the pipeline is "
                        + "vanilla's, but a replaced renderer is a replaced renderer",
                "walk from a crowd into a market with it on: figures and nameplates must both appear, "
                        + "at the same distances as without it"));
        out.add(new Mod("canary", "Canary", "server-side optimizations: loading, chunk work",
                "chunk loading and rebuilds: this mod's rebuild wait (I8) counts the chunks Terra++ "
                        + "rebuilds after a time jump",
                Verdict.CHECK_LIVE,
                "it changes when chunk work runs, and the rebuild wait's count is read from that "
                        + "work; the wait must still drain to zero and say so",
                "take a time jump with it on: the wait must reach \"the wait is over\", never stall "
                        + "and never say more chunks than were queued"));
        return out;
    }

    /** The recommended packing order: measure first, memory second, the client third, the server last. */
    public static List<String> pack() {
        return List.of(
                "1. Spark first: measure before you tune (this mod's walk is the same rule offline)",
                "2. ModernFix and FerriteCore: memory, and both check with confidence here",
                "3. Entity Culling and Embeddium: the client; run their named tests in a live game",
                "4. Canary: the server; run its time-jump test in a live game");
    }

    /** What was checked in this environment. */
    public static List<String> checkedHere() {
        List<String> out = new ArrayList<>();
        for (Mod m : mods()) {
            if (m.checkedHere()) {
                out.add(m.name() + ": " + m.reason());
            }
        }
        return out;
    }

    /** What still owes a live test, each with its test. */
    public static List<String> notCheckedHere() {
        List<String> out = new ArrayList<>();
        for (Mod m : mods()) {
            if (!m.checkedHere()) {
                out.add(m.name() + " owes a live test: " + m.liveTest());
            }
        }
        return out;
    }

    public static String ruleLine() {
        return "Checked here is not checked live: three of the six were checked against this mod's own "
                + "code and pack with confidence; three change a path this mod uses and each names the "
                + "live test it owes. No verdict claims a live check that did not happen.";
    }
}
