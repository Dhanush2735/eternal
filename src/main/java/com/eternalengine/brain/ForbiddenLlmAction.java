package com.eternalengine.brain;

import java.util.Locale;

/**
 * Explicit deny-list for unsafe direct mutations.
 */
public enum ForbiddenLlmAction {
    PLACE_BLOCK_DIRECT,
    BREAK_BLOCK_DIRECT,
    DELETE_CHUNK,
    EDIT_SAVE_FILE,
    SET_REPUTATION_DIRECT,
    MUTATE_TIMELINE_DIRECT,
    RUN_SERVER_COMMAND_RAW,
    SPAWN_ENTITY_DIRECT,
    GIVE_ITEM_DIRECT;

    public static boolean matches(String actionName) {
        if (actionName == null) {
            return false;
        }
        String normalized = actionName.trim().toUpperCase(Locale.ROOT);
        for (ForbiddenLlmAction action : values()) {
            if (action.name().equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
