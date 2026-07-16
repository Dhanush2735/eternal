package com.eternalengine.reputation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable reputation effect for one action.
 */
public final class ReputationEffect {

    private final String actionType;
    private final Map<ReputationDimension, Integer> positiveChanges;
    private final Map<ReputationDimension, Integer> negativeChanges;
    private final String description;

    public ReputationEffect(String actionType,
                            Map<ReputationDimension, Integer> positiveChanges,
                            Map<ReputationDimension, Integer> negativeChanges,
                            String description) {
        this.actionType = actionType == null || actionType.isBlank() ? "UNKNOWN_ACTION" : actionType;
        this.positiveChanges = immutableCopy(positiveChanges);
        this.negativeChanges = immutableCopy(negativeChanges);
        this.description = description == null ? "No description" : description;
    }

    public static ReputationEffect of(String actionType,
                                      Map<ReputationDimension, Integer> positive,
                                      Map<ReputationDimension, Integer> negative,
                                      String description) {
        return new ReputationEffect(actionType, positive, negative, description);
    }

    private Map<ReputationDimension, Integer> immutableCopy(Map<ReputationDimension, Integer> source) {
        Map<ReputationDimension, Integer> copy = new EnumMap<>(ReputationDimension.class);
        if (source != null) {
            for (Map.Entry<ReputationDimension, Integer> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() != 0) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public String actionType() { return actionType; }
    public Map<ReputationDimension, Integer> positiveChanges() { return positiveChanges; }
    public Map<ReputationDimension, Integer> negativeChanges() { return negativeChanges; }
    public String description() { return description; }
}
