package com.eternalengine.timeline;

import com.eternalengine.player.Permissions.Decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Fixed points: events that resist change and become paradoxes if forced.</b> G9.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 51 made changing history the safe act it should be: a branch is a difference from its parent and the
 * real history survives whatever a what-if does. G9 is about the other kind of event: the ones that <b>resist</b>.
 * Not by rolling dice. They resist by <b>refusing the in-place change and naming the safe way out</b>, and if
 * somebody forces them anyway, that forcing is what H1 counts as a paradox (destroying a dated fixed point is
 * grandfather kind).
 *
 * <h2>The two paths</h2>
 *
 * <ol>
 *   <li><b>The branch path.</b> Take the change to a timeline branch ({@code BranchSave}, step 51). That is
 *       allowed, clean, and adds nothing to the counter. This is the answer the refusal gives first.</li>
 *   <li><b>The force path.</b> An operator makes it happen in place anyway. The point is marked broken, the
 *       forcing is recorded with who and why, and its weight goes to {@link Stress} as a paradox.</li>
 * </ol>
 *
 * <p>And the way back: {@link #restore} rebuilds a broken point (H8's mechanic; H8's screen is step 56) and
 * hands back the weight, so the counter can fall as well as rise. A counter that can only rise is a countdown
 * with extra steps.
 *
 * <h2>What is fixed, and why</h2>
 *
 * <p>The register is declared, not guessed: a fixed point is a dated event the record holds, named with why it
 * is fixed (everything later leans on it) and how much forcing it costs. There is no hidden roll and no
 * probability anywhere in this class.
 */
public final class FixedPoints {

    /**
     * <b>One fixed point.</b> {@code weight} is the stress forcing it adds to {@link Stress}: a game default
     * chosen when the point is declared, not a fact of history.
     */
    public record Point(String id, int year, String what, String why, int weight) {

        public Point {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("a fixed point needs a name");
            }
            if (what == null || what.trim().isEmpty()) {
                throw new IllegalArgumentException("a fixed point must say what it is");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("a fixed point that costs nothing to force is not fixed");
            }
            id = id.trim();
            what = what.trim();
            why = why == null ? "" : why.trim();
        }

        public String line() {
            return id + " · " + year + " · " + what + " · " + weight
                    + (why.isEmpty() ? "" : " · " + why);
        }
    }

    /**
     * <b>The outcome of a forcing.</b> Either the point (and whose hand forced it), or the reason it did not
     * happen: a forcing half-done is a broken point nobody's.
     */
    public record Forced(Point point, String by, String why, String refused) {
        public boolean ok() {
            return refused.isEmpty();
        }
    }

    private final Map<String, Point> points = new LinkedHashMap<>();
    private final Map<String, String> brokenBy = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();

    private FixedPoints() {
    }

    public static FixedPoints empty() {
        return new FixedPoints();
    }

    /** Declare a fixed point. Two points with one name are refused: one name, one event. */
    public Decision declare(Point p) {
        if (p == null) {
            return Decision.no("there is no point to declare");
        }
        for (String k : points.keySet()) {
            if (k.equalsIgnoreCase(p.id())) {
                return Decision.no("there is already a fixed point called " + k
                        + "; two events with one name is a question");
            }
        }
        points.put(p.id(), p);
        log.add("declared " + p.line());
        return Decision.yes(p.id() + " is fixed: " + p.what());
    }

    /**
     * <b>Try to change it in place.</b> Always refused while the point stands, and the refusal names the safe
     * way out first: take it to a branch. That is the whole of what "resists" means here.
     */
    public Decision attempt(String id, String how) {
        Point p = find(id);
        if (p == null) {
            return Decision.no("there is no fixed point called " + id + " here");
        }
        if (brokenBy.containsKey(p.id())) {
            return Decision.no(p.id() + " was already forced by " + brokenBy.get(p.id())
                    + "; rebuild it before it can resist anything again");
        }
        return Decision.no(p.what() + " (" + p.year() + ") is a fixed point and resists " + (how == null
                ? "changing" : how.trim()) + " in place: take the change to a timeline branch, or force it "
                + "and carry the paradox");
    }

    /**
     * <b>Force it anyway.</b> The point breaks, the record says whose hand and why, and the caller takes the
     * weight to {@link Stress}. Who MAY force is not this class's question (that is {@code Permissions}:
     * forcing rewrites what is recorded); this class only records what forcing does.
     */
    public Forced force(String by, String id, String why) {
        Point p = find(id);
        if (p == null) {
            return new Forced(null, by, why, "there is no fixed point called " + id + " to force");
        }
        if (by == null || by.trim().isEmpty()) {
            return new Forced(p, by, why, "a forcing with no hand on it is nobody's; say who forced it");
        }
        if (brokenBy.containsKey(p.id())) {
            return new Forced(p, by, why, p.id() + " is already broken by " + brokenBy.get(p.id()));
        }
        if (why == null || why.trim().isEmpty()) {
            return new Forced(p, by, why, "a forcing must say why: what was wanted in place of " + p.what());
        }
        brokenBy.put(p.id(), by.trim());
        log.add("FORCED " + p.id() + " by " + by.trim() + ": " + why.trim());
        return new Forced(p, by.trim(), why.trim(), "");
    }

    /**
     * <b>Rebuild it.</b> H8's mechanic: what was broken can be put back, the hand that rebuilt it is
     * recorded, and the weight is handed back so {@link Stress} can fall.
     */
    public Decision restore(String by, String id, String why) {
        Point p = find(id);
        if (p == null) {
            return Decision.no("there is no fixed point called " + id + " here");
        }
        if (!brokenBy.containsKey(p.id())) {
            return Decision.no(p.id() + " stands unbroken; there is nothing to rebuild");
        }
        if (by == null || by.trim().isEmpty()) {
            return Decision.no("a rebuilding with no hand on it is nobody's; say who rebuilt it");
        }
        String was = brokenBy.remove(p.id());
        log.add("rebuilt " + p.id() + " by " + by.trim() + " (was broken by " + was + ")"
                + (why == null || why.trim().isEmpty() ? "" : ": " + why.trim()));
        return Decision.yes(p.id() + " is rebuilt by " + by.trim() + ", and its " + p.weight()
                + " of stress can be given back");
    }

    /** What forcing this point costs the counter. */
    public int weightOf(String id) {
        Point p = find(id);
        return p == null ? 0 : p.weight();
    }

    public Point find(String id) {
        if (id == null) {
            return null;
        }
        for (Map.Entry<String, Point> e : points.entrySet()) {
            if (e.getKey().equalsIgnoreCase(id.trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    public boolean broken(String id) {
        Point p = find(id);
        return p != null && brokenBy.containsKey(p.id());
    }

    /** Who broke it, or null. */
    public String brokenBy(String id) {
        Point p = find(id);
        return p == null ? null : brokenBy.get(p.id());
    }

    public List<Point> all() {
        return List.copyOf(points.values());
    }

    public List<Point> broken() {
        List<Point> out = new ArrayList<>();
        for (Point p : points.values()) {
            if (brokenBy.containsKey(p.id())) {
                out.add(p);
            }
        }
        return out;
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    /** The page: every point, and whether it stands. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(points.size() + " fixed points · " + brokenBy.size() + " broken");
        for (Point p : points.values()) {
            String b = brokenBy.get(p.id());
            out.add("  " + (b == null ? "" : "BROKEN ") + p.line() + (b == null ? "" : " · forced by " + b));
        }
        return out;
    }

    public static String ruleLine() {
        return "A fixed point resists by refusing the in-place change and naming the safe way out: take it to a "
                + "branch. Force it and it breaks, and the breaking is a paradox; rebuild it and the stress "
                + "falls.";
    }
}
