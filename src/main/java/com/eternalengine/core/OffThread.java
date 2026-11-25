package com.eternalengine.core;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Heavy work off the game thread: loading, planning, path searches.</b> I3.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 57's walk measured why this matters: a register's first call is its <b>load</b>, and the load of
 * the gazetteer is <b>1,388 ms</b> and of the ruler register <b>1,201 ms</b> on the test machine. One
 * second of loading on the game thread is a hundred frozen ticks. {@code core/AsyncTaskManager} is the
 * pool; what was missing is the <b>policy</b>: which classes of work may leave the game thread and which
 * may never, and the rule for coming back.
 *
 * <h2>Work is a class, not a place</h2>
 *
 * <p>Every job names its class ({@link Work}). Loading, planning, path searches, saves and network work
 * run <b>off</b> the game thread; ticking and anything that writes the world run <b>on</b> it, because
 * the world's state is only ever touched there. {@link #start} refuses a job on the wrong side by name,
 * and {@link #touchWorld} refuses a result that reaches for the world before it has been handed back.
 */
public final class OffThread {

    /** <b>Which side a job runs on.</b> */
    public enum Side {
        GAME_THREAD("on the game thread"),
        OFF_THREAD("off the game thread");

        private final String words;

        Side(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>The classes of work, and the side each belongs to.</b> */
    public enum Work {
        LOADING("loading the data registers", Side.OFF_THREAD,
                "measured 1,201 to 1,388 ms per register (the step 57 walk)"),
        PLANNING("planning: what-if runs, war plans, spawn rolls", Side.OFF_THREAD,
                "answers arrive later, and their pages say so"),
        PATH_SEARCH("path searches", Side.OFF_THREAD,
                "a search can walk the whole map while the player walks one block"),
        SAVE("writing saves and caches", Side.OFF_THREAD,
                "the disk waits for nobody"),
        NETWORK("sending and receiving packets", Side.OFF_THREAD,
                "the wire is not the world"),
        TICK("ticking the world", Side.GAME_THREAD,
                "the world's state is only ever touched here"),
        WORLD_WRITE("anything that writes the world", Side.GAME_THREAD,
                "a block change is a tick's business");

        private final String what;
        private final Side side;
        private final String why;

        Work(String what, Side side, String why) {
            this.what = what;
            this.side = side;
            this.why = why;
        }

        public String what() {
            return what;
        }

        public Side side() {
            return side;
        }

        public String why() {
            return why;
        }

        public String line() {
            return what + " · runs " + side.words() + " · " + why;
        }
    }

    /** <b>One job.</b> What it is, what class of work it is, and which side it ran on. */
    public record Job(String name, Work kind, Side side) {

        public Job {
            name = name == null ? "" : name.trim();
        }

        public String line() {
            return name + " · " + kind.what() + " · ran " + side.words();
        }
    }

    /** <b>The outcome of starting (or touching).</b> */
    public record Started(Job job, String refused) {
        public boolean ok() {
            return job != null;
        }
    }

    private OffThread() {
    }

    /**
     * <b>Start a job, on its right side or not at all.</b> Refused: work with no name ("work nobody can
     * account for"), and a job started on the wrong side, naming the side it belongs to and why.
     */
    public static Started start(String name, Work kind, Side side) {
        List<String> refused = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) {
            refused.add("work with no name is work nobody can account for: say what this job is");
        }
        if (kind == null) {
            refused.add("a job that names no class of work is no job: loading, planning, a path search, "
                    + "a save, the network, ticking, or a world write");
        }
        if (kind != null && side != null && kind.side() != side) {
            refused.add(kind.what() + " runs " + kind.side().words() + ", not " + side.words() + ": "
                    + kind.why());
        }
        if (side == null) {
            refused.add("say where it runs: the game thread or off it");
        }
        if (!refused.isEmpty()) {
            return new Started(null, String.join("; ", refused));
        }
        return new Started(new Job(name, kind, side), "");
    }

    /**
     * <b>A result that touches the world.</b> Allowed on the game thread only. A result coming back
     * from off-thread must be handed back first, and this refuses the shortcut in plain words.
     */
    public static Started touchWorld(String name, Work kind, Side side) {
        if (side == Side.OFF_THREAD) {
            return new Started(null, (name == null ? "the result" : name.trim())
                    + " touches the world and it ran off the game thread: hand it back first, and let "
                    + "the game thread write");
        }
        return start(name, kind, side);
    }

    /** The page: every class of work and its side, in words. */
    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Work w : Work.values()) {
            out.add(w.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "Work is a class, not a place: loading, planning, path searches, saves and the network "
                + "run off the game thread; ticking and world writes run on it. A result that touches "
                + "the world comes back first, and a job on the wrong side is refused by name.";
    }
}
