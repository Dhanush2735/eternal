package com.eternalengine.timeline;

import com.eternalengine.incident.IncidentSeed;

/**
 * Deterministic seed for alternative timelines.
 *
 * Same world seed + same parent branch + same divergence + same player action
 * must always generate the same branch identity and event rolls.
 */
public final class TimelineSeed {

    private final long worldSeed;
    private final String parentBranchId;
    private final String divergenceEventId;
    private final int divergenceYear;
    private final String playerActionHash;

    public TimelineSeed(long worldSeed, String parentBranchId, String divergenceEventId,
                        int divergenceYear, String playerActionHash) {
        this.worldSeed = worldSeed;
        this.parentBranchId = safe(parentBranchId, "main");
        this.divergenceEventId = safe(divergenceEventId, "unknown_event");
        this.divergenceYear = divergenceYear;
        this.playerActionHash = safe(playerActionHash, "no_player_action");
    }

    public long value() {
        return IncidentSeed.stableLong(worldSeed, parentBranchId, divergenceEventId, divergenceYear, playerActionHash);
    }

    public String branchId() {
        String hex = IncidentSeed.stableHex(worldSeed, parentBranchId, divergenceEventId, divergenceYear, playerActionHash);
        return "branch_" + hex.substring(0, Math.min(24, hex.length()));
    }

    public double roll(int year, String eventKey) {
        long raw = IncidentSeed.stableLong(value(), year, safe(eventKey, "event"));
        long positive = raw & Long.MAX_VALUE;
        return (positive % 10_000L) / 10_000.0d;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public long worldSeed() { return worldSeed; }
    public String parentBranchId() { return parentBranchId; }
    public String divergenceEventId() { return divergenceEventId; }
    public int divergenceYear() { return divergenceYear; }
    public String playerActionHash() { return playerActionHash; }
}
