package com.eternalengine.knowledge;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Who knows what. Level 1: street knowledge, what everyone in the bazaar is saying. Level 2: court
 * knowledge, the ministers and the reports, known to people who serve at court or keep records.
 */
public final class KnowledgeLevels {

    private static final Set<String> COURT = Set.of("court_chancellor", "sovereign_ruler", "scholar", "priest",
            "commander", "scribe", "advisor");

    private static final Map<String, String> JOBS = Map.ofEntries(
            Map.entry("citizen", "townsperson"),
            Map.entry("villager", "villager"),
            Map.entry("farmer", "farmer"),
            Map.entry("artisan", "craftsman"),
            Map.entry("merchant", "merchant"),
            Map.entry("money_changer", "money-changer"),
            Map.entry("trader", "trader"),
            Map.entry("pedlar", "pedlar"),
            Map.entry("paper_seller", "paper seller"),
            Map.entry("news_writer", "newsletter writer"),
            Map.entry("town_crier", "town crier"),
            Map.entry("soldier", "soldier"),
            Map.entry("guard", "guard"),
            Map.entry("royal_guard", "palace guard"),
            Map.entry("scholar", "scholar"),
            Map.entry("scribe", "court clerk"),
            Map.entry("priest", "priest"),
            Map.entry("healer", "healer"),
            Map.entry("advisor", "minister"),
            Map.entry("court_chancellor", "chancellor"),
            Map.entry("sovereign_ruler", "ruler"),
            Map.entry("commander", "commander"),
            Map.entry("caravan_master", "caravan master"),
            Map.entry("caravan_guard", "caravan guard"),
            Map.entry("quarantine_inspector", "health inspector"),
            Map.entry("bandit", "stranger"),
            Map.entry("rival_infiltrator", "traveller"));

    private KnowledgeLevels() {
    }

    public static int of(String role) {
        return role != null && COURT.contains(role.toLowerCase(Locale.ROOT)) ? 2 : 1;
    }

    /** The job in plain words: "court clerk", "farmer". */
    public static String job(String role) {
        if (role == null || role.isBlank()) {
            return "townsperson";
        }
        String r = role.toLowerCase(Locale.ROOT);
        return JOBS.getOrDefault(r, r.replace('_', ' '));
    }

    /** "How do you know?" in the villager's own words. Never a source name. */
    public static String howIKnow(int level, String kind, boolean far) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (level >= 2) {
            return switch (k) {
                case "person", "family" -> "I have served at court for years. One learns who is who.";
                case "place" -> far ? "Reports from there reach the court." : "Everyone at court knows that.";
                default -> "Reports come to the court every day. I hear them read.";
            };
        }
        return switch (k) {
            case "person", "family" -> "Everybody knows that.";
            case "place" -> far ? "Travellers talk about it." : "I've lived here all my life.";
            case "state" -> "Everybody knows that.";
            case "group" -> far ? "Travellers talk about it." : "Everybody here knows it."; // step 27: companies, schools, ...
            default -> "Everyone in the bazaar is talking about it.";
        };
    }
}
