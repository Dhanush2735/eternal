package com.eternalengine.history;

public enum TriggerOperator {
    EXISTS,
    NOT_EXISTS,
    GREATER_THAN,
    LESS_THAN,
    EQUALS;

    public static TriggerOperator parse(String value) {
        if (value == null || value.isBlank()) {
            return EXISTS;
        }
        try {
            return TriggerOperator.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EXISTS;
        }
    }
}
