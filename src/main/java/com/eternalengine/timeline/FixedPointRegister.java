package com.eternalengine.timeline;

/**
 * The fixed points of this world, declared, with the one counter they move.
 *
 * <h2>Why this class exists at all (marking board 14, 2026-10-23)</h2>
 *
 * <p>{@link FixedPoints} was complete — declare, attempt, force, restore, weigh — and
 * {@link ParadoxEngine} and {@link Repairs} both took a register as an argument. But no code ever
 * <em>made</em> one: a grep for {@code FixedPoints.empty()} found only its own declaration, and a
 * grep of {@code commands/} found nothing at all. So in a running game the register was empty, no
 * command could reach it, and the policy in G5 of {@code FINAL_TEST.md} ("try to change a fixed
 * point … it refuses and names the safe way out") could not happen to a player. FINAL_TEST G5 was
 * unsatisfiable by construction, not by bug.
 *
 * <p>This class declares the points the record actually holds, and holds the {@link Stress} counter
 * they are booked against. The ids are event ids from the mod's own packs, so a point is never a
 * name that resolves to nothing; {@link #unknownAmong} reports any that stopped resolving when the
 * packs are rebuilt.
 *
 * <h2>What makes a point fixed here</h2>
 *
 * <p>Not fame: <em>weight</em>. A fixed point is an event that everything later leans on, so forcing
 * it is expensive — the numbers below are game defaults, not facts of history (the class's own
 * contract). Each carries the reason in words, because {@code Point.line()} prints it and a player
 * who is refused deserves the reason, not a dice roll.
 */
public final class FixedPointRegister {

    /** The one register, declared once. */
    private static final FixedPoints POINTS = declare();

    /** The counter every forcing and rebuilding is booked against. */
    private static final Stress STRESS = Stress.empty();

    private FixedPointRegister() {
    }

    public static FixedPoints points() {
        return POINTS;
    }

    public static Stress stress() {
        return STRESS;
    }

    /**
     * The declaration. Every id below is an id in a shipped event pack (the pack is named in the
     * comment); the year is the pack's own first year. A point whose id stops resolving is reported
     * by {@link #unknownAmong} rather than silently dropped — a fixed point that nobody can name is
     * worse than a missing one.
     */
    private static FixedPoints declare() {
        FixedPoints out = FixedPoints.empty();
        // q758173 · events_nature.json.gz · the plague that opened Thucydides' history
        out.declare(new FixedPoints.Point("q758173", -430,
                "the Plague of Athens",
                "the war, the funeral speech and the Athenian turn all lean on it", 6));
        // wpw_14cd144a95 · events_wars.json.gz · the Republic's last civil war
        out.declare(new FixedPoints.Point("wpw_14cd144a95", -49,
                "Caesar's Civil War",
                "the Republic never governs again after it; the Empire is the sequel", 8));
        // q42005 · events_world.json.gz · the Black Death
        out.declare(new FixedPoints.Point("q42005", 1346,
                "the Black Death",
                "a third of Europe dies: wages, land and faith all move for a century", 9));
        // wpd_282f01cbca · events_wikipedia_decades.json.gz · Magna Carta
        out.declare(new FixedPoints.Point("wpd_282f01cbca", 1215,
                "King John signs Magna Carta at Runnymede",
                "the crown is written under law, and every later charter cites it", 7));
        // wpr_5a6fa00926 · events_wikipedia_regional.json.gz · movable type in Europe
        out.declare(new FixedPoints.Point("wpr_5a6fa00926", 1450,
                "Gutenberg sets up his movable-type printing press",
                "the printed book makes the Reformation and the scientific press possible", 8));
        // wpr_5133f16bb5 · events_wikipedia_regional.json.gz · Sputnik 1
        out.declare(new FixedPoints.Point("wpr_5133f16bb5", 1957,
                "the Soviet Union launches Sputnik 1",
                "the space age and the satellite economy both date from it", 5));
        return out;
    }

    /**
     * Ids that no longer resolve to an event in the packs the caller can see. The command side asks
     * this so a register that has rotted says so instead of pretending.
     */
    public static java.util.List<String> unknownAmong(java.util.function.Predicate<String> exists) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (FixedPoints.Point p : POINTS.all()) {
            if (!exists.test(p.id())) {
                out.add(p.id());
            }
        }
        return out;
    }

    /** The register as pages, for the command. */
    public static java.util.List<String> lines() {
        return POINTS.lines();
    }
}
