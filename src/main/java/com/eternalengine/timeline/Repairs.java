package com.eternalengine.timeline;

import com.eternalengine.player.Permissions.Decision;

/**
 * <b>Repair: rebuilding a fixed point lowers the stress.</b> H8.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 53 built both halves and left this screen for here: {@link FixedPoints#restore} rebuilds a broken
 * point and hands its weight back ("its N of stress can be given back"), and {@link Stress#repair} brings
 * the counter down (clamped at zero, saying how much was unused). H8 is the sentence between them:
 * <b>one call</b> that rebuilds the point, gives the weight back, and reports the fall in one line -
 * "rebuilding X lowered the stress from A to B".
 *
 * <h2>Why one call</h2>
 *
 * <p>Two calls is two chances to do half the work: a point rebuilt while the counter keeps its weight is
 * a lie the books will tell forever. This class cannot do one without the other, and when the counter is
 * already at zero the clamp's own words come back in the report rather than being swallowed.
 */
public final class Repairs {

    /**
     * <b>What the repair did.</b> The point, the counter before and after, how much was given back, and
     * every reason it could not happen (empty when it did).
     */
    public record Repaired(FixedPoints.Point point, int before, int after, int given, String refused) {

        public Repaired {
            refused = refused == null ? "" : refused.trim();
        }

        public boolean ok() {
            return point != null;
        }

        public String line() {
            if (!ok()) {
                return "no repair: " + refused;
            }
            if (given > 0) {
                return "rebuilding " + point.id() + " (" + point.what() + ") lowered the stress from "
                        + before + " to " + after + ": its weight of " + given + " given back";
            }
            return "rebuilding " + point.id() + " (" + point.what() + ") is done, and its weight of "
                    + point.weight() + " gave nothing back: the counter was already at " + before;
        }
    }

    private Repairs() {
    }

    /**
     * <b>Rebuild the point and lower the stress.</b> Every refusal of {@link FixedPoints#restore} is
     * carried through in its own words: no such point, an unbroken point ("there is nothing to
     * rebuild"), a rebuilding with no hand on it. The fall is the point's own weight, and the clamp at
     * zero is {@link Stress#repair}'s, reported rather than swallowed.
     */
    public static Repaired repair(FixedPoints pts, Stress stress, String by, String id, int year,
                                  String why) {
        if (pts == null) {
            return new Repaired(null, 0, 0, 0, "there is no register of fixed points to rebuild from");
        }
        if (stress == null) {
            return new Repaired(null, 0, 0, 0, "there is no counter to lower");
        }
        int before = stress.total();
        FixedPoints.Point point = pts.find(id == null ? "" : id.trim());
        Decision did = pts.restore(by, id, why);
        if (!did.allowed()) {
            return new Repaired(null, before, before, 0, did.why());
        }
        int weight = pts.weightOf(id == null ? "" : id.trim());
        if (weight < 1) {
            // the point is rebuilt; the counter has nothing to give. The clamp's silence is not allowed:
            // the report says the weight gave nothing back.
            return new Repaired(point, before, before, 0, "");
        }
        Stress.Changed change = stress.repair(by, year, point.id(), weight,
                "rebuilt " + point.id() + (why == null || why.trim().isEmpty() ? "" : ": " + why.trim()));
        if (!change.ok()) {
            return new Repaired(point, before, stress.total(), 0, change.refused());
        }
        // what actually fell, not what was asked for: the clamp keeps the difference and the books
        // say how much was unused, but this report must never claim a fall that did not happen.
        int after = stress.total();
        return new Repaired(point, before, after, Math.max(0, before - after), "");
    }

    /** The page: several repairs, one line each. */
    public static String screen(java.util.List<Repaired> repairs) {
        if (repairs == null || repairs.isEmpty()) {
            return "nothing has been rebuilt";
        }
        StringBuilder b = new StringBuilder();
        for (Repaired r : repairs) {
            if (r != null) {
                if (b.length() > 0) {
                    b.append(" | ");
                }
                b.append(r.line());
            }
        }
        return b.toString();
    }

    public static String ruleLine() {
        return "Rebuilding a fixed point lowers the stress by the point's own weight, in one call. A "
                + "rebuilding with no hand is nobody's; an unbroken point has nothing to rebuild; and "
                + "the counter clamps at zero and says how much of the repair was unused.";
    }
}
