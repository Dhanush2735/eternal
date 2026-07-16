package com.eternalengine.player;

/**
 * Player actions relevant to history, reputation, and divergence checks.
 */
public enum PlayerActionType {
    KILL_VILLAGER,
    KILL_PLAYER,
    KILL_HOSTILE,
    KILL_VIP_CANDIDATE,
    BREAK_BLOCK,
    PLACE_BLOCK,
    INTERACT_BLOCK,
    PICKUP_ITEM,
    CLAIM_VIP,
    RELEASE_VIP,
    COMMAND_ACTION,
    UNKNOWN
}
