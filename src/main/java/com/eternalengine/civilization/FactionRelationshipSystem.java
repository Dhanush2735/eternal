package com.eternalengine.civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bilateral faction relationship foundation.
 */
public final class FactionRelationshipSystem {
    private final ConcurrentHashMap<String, RelationshipData> relations = new ConcurrentHashMap<>();

    public RelationshipData getRelationship(String civ1, String civ2) {
        return relations.computeIfAbsent(key(civ1, civ2), k -> new RelationshipData(RelationshipType.NEUTRAL, 0));
    }

    public void setRelationship(String civ1, String civ2, RelationshipType type) {
        RelationshipData data = getRelationship(civ1, civ2);
        data.type = type == null ? RelationshipType.NEUTRAL : type;
        data.history.add("set:" + data.type);
    }

    public void adjustScore(String civ1, String civ2, int delta) {
        RelationshipData data = getRelationship(civ1, civ2);
        data.score = Math.max(-100, Math.min(100, data.score + delta));
        data.type = typeFromScore(data.score);
        data.history.add("score:" + data.score);
    }

    public boolean isAtWar(String civ1, String civ2) {
        return getRelationship(civ1, civ2).type == RelationshipType.WAR;
    }

    public boolean isAllied(String civ1, String civ2) {
        return getRelationship(civ1, civ2).type == RelationshipType.ALLIED;
    }

    public boolean hasTradeAgreement(String civ1, String civ2) {
        RelationshipType type = getRelationship(civ1, civ2).type;
        return type == RelationshipType.TRADE_PARTNER || type == RelationshipType.ALLIED;
    }

    public List<String> getEnemies(String civId) {
        return filterBy(civId, RelationshipType.WAR, RelationshipType.HOSTILE);
    }

    public List<String> getAllies(String civId) {
        return filterBy(civId, RelationshipType.ALLIED);
    }

    public List<String> getTreatiesOf(String civId) {
        List<String> result = new ArrayList<>();
        for (var entry : relations.entrySet()) {
            if (entry.getKey().contains(civId) && !entry.getValue().treatyIds.isEmpty()) {
                result.addAll(entry.getValue().treatyIds);
            }
        }
        return List.copyOf(result);
    }

    private List<String> filterBy(String civId, RelationshipType... types) {
        List<String> result = new ArrayList<>();
        for (var entry : relations.entrySet()) {
            if (!entry.getKey().contains(civId)) continue;
            for (RelationshipType type : types) {
                if (entry.getValue().type == type) {
                    String[] parts = entry.getKey().split(":", 2);
                    result.add(parts[0].equals(civId) ? parts[1] : parts[0]);
                }
            }
        }
        return List.copyOf(result);
    }

    private RelationshipType typeFromScore(int score) {
        if (score <= -75) return RelationshipType.WAR;
        if (score <= -35) return RelationshipType.HOSTILE;
        if (score >= 75) return RelationshipType.ALLIED;
        if (score >= 35) return RelationshipType.TRADE_PARTNER;
        return RelationshipType.NEUTRAL;
    }

    private String key(String civ1, String civ2) {
        String a = civ1 == null ? "unknown_a" : civ1;
        String b = civ2 == null ? "unknown_b" : civ2;
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    public static final class RelationshipData {
        private RelationshipType type;
        private int score;
        private final List<String> treatyIds = new ArrayList<>();
        private final List<String> history = new ArrayList<>();

        public RelationshipData(RelationshipType type, int score) {
            this.type = type;
            this.score = score;
        }

        public RelationshipType type() { return type; }
        public int score() { return score; }
        public List<String> treatyIds() { return List.copyOf(treatyIds); }
        public List<String> history() { return List.copyOf(history); }
        public void addTreaty(String treatyId) { if (treatyId != null) treatyIds.add(treatyId); }
    }
}
