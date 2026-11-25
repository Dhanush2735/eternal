package com.eternalengine.chrononexus;

/**
 * The 3 Distinct Moral Endings of Chrono-Nexus: The Temporal Paradox.
 */
public enum ChronoEnding {
    INFINITE_HORIZON(
        "The Infinite Horizon (Freedom)",
        "Destroy the Citadel and Dimension Machines, leaving human history open, branching, and free across infinite timelines."
    ),
    SOVEREIGN_CYCLE(
        "The Sovereign Cycle (The Dark Loop)",
        "Take the throne of the Citadel to police the multiverse, tragically becoming the next generation's Rival."
    ),
    SEVERED_KNOT(
        "The Severed Knot (Total Sacrifice)",
        "Shatter all temporal and dimensional technology, returning to the present day to live a normal life without paradoxes."
    );

    private final String title;
    private final String description;

    ChronoEnding(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static ChronoEnding fromString(String key) {
        if (key == null) return INFINITE_HORIZON;
        for (ChronoEnding ending : values()) {
            if (ending.name().equalsIgnoreCase(key) || ending.name().replace("_", "").equalsIgnoreCase(key.replace("_", ""))) {
                return ending;
            }
        }
        return INFINITE_HORIZON;
    }
}
