package com.eternalengine.affairs;

/**
 * One server-provided fact. Bots may rephrase this but must not invent facts.
 */
public record CurrentAffairsFact(String category, String text, int priority) {
    public CurrentAffairsFact {
        if (category == null || category.isBlank()) {
            category = "general";
        }
        if (text == null || text.isBlank()) {
            text = "No detail available.";
        }
        priority = Math.max(0, Math.min(100, priority));
    }
}
