package com.eternalengine.timeline;

/**
 * Where a chronicle line came from.
 *
 * <p>Eternal Engine's history corpus already distinguishes {@code confidence: wikidata} from
 * {@code confidence: estimated}. Divergence needs the same discipline: a branch mixes things the
 * world observed, things the data implies, things a person wrote, and things the engine merely
 * modelled. <b>A line that does not say which one it is has no value.</b>
 */
public enum Provenance {

    /**
     * The world itself. A suppressed pin leaves an empty square, and the empty square is a fact.
     * No claim is being made, so nothing can be wrong.
     */
    OBSERVED("observed", "§7"),

    /**
     * Derived from data identity rather than causation — a sibling pin at the same place and time
     * is the same real-world event. Safe, because it asserts sameness, not consequence.
     */
    STRUCTURAL("structural", "§b"),

    /**
     * A human wrote this consequence and signed it. Carries the weight of a claim, and must be
     * checkable against the record.
     */
    AUTHORED("authored", "§a"),

    /**
     * The simulation's estimate. Plausible by construction, specific by accident. Always labelled,
     * in the same spirit as {@code confidence: estimated} in the history corpus.
     */
    MODELLED("modelled", "§e");

    private final String label;
    private final String colour;

    Provenance(String label, String colour) {
        this.label = label;
        this.colour = colour;
    }

    /** Machine-readable name used in save files and scenario JSON. */
    public String label() {
        return label;
    }

    /** Minecraft formatting code for in-game display. */
    public String colour() {
        return colour;
    }

    /** Short tag the player sees next to the line. */
    public String tag() {
        return colour + "(" + label + ")§r";
    }

    public static Provenance parse(String raw) {
        if (raw == null) {
            return MODELLED;
        }
        String key = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (Provenance p : values()) {
            if (p.label.equals(key) || p.name().equalsIgnoreCase(key)) {
                return p;
            }
        }
        return MODELLED;
    }
}
