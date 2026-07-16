package com.eternalengine.player;

import java.util.UUID;

/**
 * Immutable action observation from a server event.
 */
public record PlayerAction(
    UUID playerUuid,
    String playerName,
    PlayerActionType type,
    int year,
    String dimension,
    int blockX,
    int blockY,
    int blockZ,
    String details,
    long timestampMs
) {
    public PlayerAction {
        type = type == null ? PlayerActionType.UNKNOWN : type;
        playerName = playerName == null ? "unknown" : playerName;
        dimension = dimension == null ? "unknown" : dimension;
        details = details == null ? "" : details;
    }
}
