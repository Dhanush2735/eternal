package com.eternalengine.affairs;

import java.util.List;

/**
 * Role-specific current affairs focus and explanation prefix.
 */
public final class BotAffairsPersonality {

    private BotAffairsPersonality() {
    }

    public static List<String> focus(BotAffairsRole role) {
        if (role == null) {
            role = BotAffairsRole.VILLAGER;
        }
        return switch (role) {
            case SOLDIER -> List.of("wars", "frontlines", "army morale", "supply", "danger");
            case MERCHANT -> List.of("trade", "prices", "routes", "taxes", "vehicle safety");
            case HEALER -> List.of("disease", "medicine", "quarantine", "mortality", "hospitals");
            case PRIEST -> List.of("religion", "morale", "heresy", "unrest", "holy sites");
            case ADVISOR -> List.of("politics", "reputation", "incidents", "stability", "anomalies");
            case HISTORIAN -> List.of("timeline", "divergence", "what changed", "major events", "anomalies");
            case VILLAGER -> List.of("local safety", "food", "work", "rumors", "nearby danger");
        };
    }

    public static String prefix(BotAffairsRole role) {
        if (role == null) {
            role = BotAffairsRole.VILLAGER;
        }
        return switch (role) {
            case SOLDIER -> "Military report";
            case MERCHANT -> "Market report";
            case HEALER -> "Health report";
            case PRIEST -> "Spiritual report";
            case ADVISOR -> "Advisor briefing";
            case HISTORIAN -> "Historian briefing";
            case VILLAGER -> "Local news";
        };
    }
}
