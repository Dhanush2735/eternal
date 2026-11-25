package com.eternalengine.terrabridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Waiting while Terra++ rebuilds after a time jump.</b> I8.
 *
 * <h2>Why this class exists</h2>
 *
 * {@code TerraTimeBridge} already queues every loaded chunk for rebuilding after a time jump ("as
 * /terra timejump does"). What nothing held was the <b>wait</b>: the state the game stands in while
 * that queue drains. A rebuild of a whole world is thousands of chunks; without a state, the game either
 * stalls silently or pretends the world is ready when it is not.
 *
 * <h2>The wait is a state, not a stall</h2>
 *
 * <p>It names what is queued, what is rebuilt, and what is left; it answers in words at every point
 * ("waiting for Terra++: 40 of 120 chunks rebuilt"); it refuses a second wait while one runs (a time
 * jump during a rebuild is how worlds corrupt); and it ends when the queue drains, saying so. With no
 * Terra++, there is nothing to wait for and the wait says that too.
 */
public final class RebuildWait {

    /** <b>One wait.</b> */
    public record Wait(String id, int queued, int rebuilt, boolean running, String why) {

        public Wait {
            id = id == null ? "" : id.trim();
            why = why == null ? "" : why.trim();
        }

        public int left() {
            return Math.max(0, queued - rebuilt);
        }

        public String words() {
            if (!running && rebuilt >= queued) {
                return "the wait is over: " + queued + " chunks rebuilt" + (why.isEmpty() ? "" : " ("
                        + why + ")");
            }
            return "waiting for Terra++: " + rebuilt + " of " + queued + " chunks rebuilt, "
                    + left() + " left" + (why.isEmpty() ? "" : " (" + why + ")");
        }

        public String line() {
            return id + " · " + words();
        }
    }

    /** <b>The outcome of starting or finishing a wait.</b> (The component is `state`: `wait()` would
     *  clash with {@code Object.wait()}, and Java does not allow that.) */
    public record Started(Wait state, String refused) {
        public boolean ok() {
            return state != null;
        }
    }

    private final Map<String, Wait> waits = new LinkedHashMap<>();
    private int counter = 0;

    private RebuildWait() {
    }

    public static RebuildWait empty() {
        return new RebuildWait();
    }

    /**
     * <b>Begin the wait.</b> Refused: nothing queued (the world is already right: a wait for nothing is
     * a stall), a queue of below none, and a second wait while one runs - a time jump during a rebuild
     * is how worlds corrupt.
     */
    public synchronized Started begin(int queued, String why) {
        for (Wait w : waits.values()) {
            if (w.running()) {
                return new Started(null, "a rebuild is already running (" + w.id() + ", "
                        + w.words() + "): a time jump during a rebuild is how worlds corrupt");
            }
        }
        if (queued <= 0) {
            return new Started(null, "nothing is queued: the world is already right, and a wait for "
                    + "nothing is a stall");
        }
        counter++;
        Wait w = new Wait("rebuild-" + counter, queued, 0, true,
                why == null ? "" : why.trim());
        waits.put(w.id(), w);
        return new Started(w, "");
    }

    /**
     * <b>Chunks rebuilt.</b> Refused: an unknown wait, and more rebuilt than were queued (the books say
     * how many there were; this does not invent chunks).
     */
    public synchronized Started progress(String id, int rebuilt) {
        Wait w = id == null ? null : waits.get(id.trim());
        if (w == null) {
            return new Started(null, "there is no wait called " + id);
        }
        if (!w.running()) {
            return new Started(null, w.id() + " is already over: " + w.words());
        }
        if (rebuilt < 0) {
            return new Started(null, "rebuilt " + rebuilt + " is below none");
        }
        if (rebuilt > w.queued()) {
            return new Started(null, "the books say " + w.queued() + " chunks were queued; " + rebuilt
                    + " rebuilt would invent chunks nobody queued");
        }
        Wait next = new Wait(w.id(), w.queued(), rebuilt, rebuilt < w.queued(), w.why());
        waits.put(next.id(), next);
        return new Started(next, "");
    }

    /** This wait, or null. */
    public synchronized Wait find(String id) {
        return id == null ? null : waits.get(id.trim());
    }

    /** Is any rebuild running? */
    public synchronized boolean running() {
        for (Wait w : waits.values()) {
            if (w.running()) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Wait w : waits.values()) {
            out.add(w.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "The wait is a state, not a stall: it names what is queued, what is rebuilt and what is "
                + "left; a second wait while one runs is refused; more rebuilt than queued invents "
                + "chunks and is refused; and when the queue drains the wait says it is over.";
    }
}
