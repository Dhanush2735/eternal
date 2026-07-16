package com.eternalengine.history;

import com.eternalengine.core.WorldStateManager;
import com.google.gson.JsonObject;

/**
 * Event trigger condition. Condition IDs are deterministic keys that read from
 * WorldStateManager, for example:
 * - flag:rome_exists
 * - population:italy
 * - war:ww1
 * - disease:black_death
 * - civ:rome
 * - tech:rome
 */
public final class TriggerCondition {

    private final String conditionId;
    private final boolean required;
    private final double threshold;
    private final TriggerOperator operator;
    private final String expectedValue;

    public TriggerCondition(String conditionId, boolean required, double threshold,
                            TriggerOperator operator, String expectedValue) {
        this.conditionId = conditionId == null || conditionId.isBlank() ? "flag:unknown" : conditionId;
        this.required = required;
        this.threshold = threshold;
        this.operator = operator == null ? TriggerOperator.EXISTS : operator;
        this.expectedValue = expectedValue == null ? "" : expectedValue;
    }

    public static TriggerCondition fromJson(JsonObject json) {
        if (json == null) {
            return new TriggerCondition("flag:invalid", false, 0.0d, TriggerOperator.NOT_EXISTS, "");
        }
        String id = getString(json, "conditionId", getString(json, "id", "flag:unknown"));
        boolean required = json.has("required") && json.get("required").getAsBoolean();
        double threshold = json.has("threshold") ? json.get("threshold").getAsDouble() : 0.0d;
        TriggerOperator op = TriggerOperator.parse(getString(json, "operator", "EXISTS"));
        String expected = getString(json, "value", getString(json, "expectedValue", ""));
        return new TriggerCondition(id, required, threshold, op, expected);
    }

    public boolean evaluate(WorldStateManager state) {
        if (state == null) {
            return !required;
        }
        ConditionValue value = readValue(state);
        boolean result = switch (operator) {
            case EXISTS -> value.exists();
            case NOT_EXISTS -> !value.exists();
            case GREATER_THAN -> value.numeric() > threshold;
            case LESS_THAN -> value.numeric() < threshold;
            case EQUALS -> expectedValue.isBlank()
                ? Double.compare(value.numeric(), threshold) == 0
                : expectedValue.equalsIgnoreCase(value.text());
        };
        return required ? result : true;
    }

    private ConditionValue readValue(WorldStateManager state) {
        String[] parts = conditionId.split(":", 2);
        String domain = parts.length > 0 ? parts[0] : "flag";
        String key = parts.length > 1 ? parts[1] : conditionId;
        return switch (domain) {
            case "flag" -> {
                String flag = state.getFlag(key);
                yield new ConditionValue(flag != null, flag == null ? "" : flag, parseDouble(flag));
            }
            case "population" -> new ConditionValue(true, Long.toString(state.getPopulation(key)), state.getPopulation(key));
            case "war" -> new ConditionValue(state.getWar(key) != null, key, state.getWar(key) == null ? 0 : 1);
            case "disease" -> new ConditionValue(state.getDisease(key) != null, key, state.getDisease(key) == null ? 0 : 1);
            case "civ" -> new ConditionValue(state.getCivilization(key) != null, key, state.getCivilization(key) == null ? 0 : 1);
            case "tech" -> {
                var civ = state.getCivilization(key);
                yield new ConditionValue(civ != null, key, civ == null ? 0 : civ.techLevel);
            }
            case "timeline" -> new ConditionValue(key.equals(state.getActiveTimelineBranchId()), state.getActiveTimelineBranchId(), 0);
            default -> {
                String flag = state.getFlag(conditionId);
                yield new ConditionValue(flag != null, flag == null ? "" : flag, parseDouble(flag));
            }
        };
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static double parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private record ConditionValue(boolean exists, String text, double numeric) {}

    public String conditionId() { return conditionId; }
    public boolean required() { return required; }
    public double threshold() { return threshold; }
    public TriggerOperator operator() { return operator; }
    public String expectedValue() { return expectedValue; }
}
