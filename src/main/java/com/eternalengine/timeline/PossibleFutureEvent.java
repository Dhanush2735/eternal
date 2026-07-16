package com.eternalengine.timeline;

/**
 * A historically related future event that may still occur in altered form.
 */
public final class PossibleFutureEvent {

    private final String originalEventId;
    private final int probability;
    private final String newForm;
    private final String conditions;

    public PossibleFutureEvent(String originalEventId, int probability, String newForm, String conditions) {
        this.originalEventId = originalEventId == null || originalEventId.isBlank() ? "unknown_event" : originalEventId;
        this.probability = Math.max(0, Math.min(100, probability));
        this.newForm = newForm == null ? "unknown" : newForm;
        this.conditions = conditions == null ? "No conditions recorded" : conditions;
    }

    public String originalEventId() { return originalEventId; }
    public int probability() { return probability; }
    public String newForm() { return newForm; }
    public String conditions() { return conditions; }
}
