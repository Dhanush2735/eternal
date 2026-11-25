package com.eternalengine.chrononexus;

/**
 * Game Modes for Chrono-Nexus: The Temporal Paradox.
 */
public enum ChronoGameMode {
    EXPLORATION("Exploration (Creative/Sandbox) - Free temporal navigation without rival incursions or causal decay"),
    STORY("Story (Survival Campaign) - High-stakes survival, rival incursions, paradox loops, and grandfather erasure");

    private final String description;

    ChronoGameMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
