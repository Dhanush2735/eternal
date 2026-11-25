package com.eternalengine.sovereign;

import com.eternalengine.civilization.FactionRelationshipSystem;
import com.eternalengine.civilization.RelationshipType;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.war.WarEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geopolitical Diplomacy & Treaty Engine.
 * Manages formal bilateral treaties, mutual defense pacts, trade agreements,
 * and coercive ultimatums backed by military consequences.
 */
public final class DiplomaticTreatyEngine {

    public enum TreatyType {
        NON_AGGRESSION_PACT,
        DEFENSIVE_ALLIANCE,
        EXCLUSIVE_TRADE_ACCORD,
        TRIBUTARY_VASSALAGE
    }

    public enum UltimatumDemand {
        DEMAND_TRIBUTE,
        DEMAND_TERRITORY,
        DEMAND_PEACE,
        DEMAND_DISARMAMENT
    }

    public record TreatyRecord(
        String treatyId,
        String civA,
        String civB,
        TreatyType type,
        int signedYear,
        int expirationYear,
        long annualTribute
    ) {}

    public static final class UltimatumRecord {
        private final String ultimatumId;
        private final String senderCiv;
        private final String targetCiv;
        private final UltimatumDemand demand;
        private final int deadlineYear;
        private boolean resolved = false;
        private boolean accepted = false;

        public UltimatumRecord(String ultimatumId, String senderCiv, String targetCiv,
                               UltimatumDemand demand, int deadlineYear) {
            this.ultimatumId = ultimatumId;
            this.senderCiv = senderCiv;
            this.targetCiv = targetCiv;
            this.demand = demand;
            this.deadlineYear = deadlineYear;
        }

        public String ultimatumId() { return ultimatumId; }
        public String senderCiv() { return senderCiv; }
        public String targetCiv() { return targetCiv; }
        public UltimatumDemand demand() { return demand; }
        public int deadlineYear() { return deadlineYear; }
        public boolean isResolved() { return resolved; }
        public boolean isAccepted() { return accepted; }
        public void resolve(boolean accepted) {
            this.resolved = true;
            this.accepted = accepted;
        }
    }

    private final ConcurrentHashMap<String, TreatyRecord> activeTreaties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UltimatumRecord> pendingUltimatums = new ConcurrentHashMap<>();

    public TreatyRecord signTreaty(String civA, String civB, TreatyType type,
                                   int currentYear, int durationYears, long annualTribute,
                                   FactionRelationshipSystem relations) {
        if (civA == null || civB == null || type == null) return null;

        String id = "treaty_" + civA + "_" + civB + "_" + currentYear;
        TreatyRecord treaty = new TreatyRecord(id, civA, civB, type, currentYear, currentYear + durationYears, annualTribute);
        activeTreaties.put(id, treaty);

        if (relations != null) {
            switch (type) {
                case NON_AGGRESSION_PACT -> relations.setRelationship(civA, civB, RelationshipType.NEUTRAL);
                case DEFENSIVE_ALLIANCE -> relations.setRelationship(civA, civB, RelationshipType.ALLIED);
                case EXCLUSIVE_TRADE_ACCORD -> relations.setRelationship(civA, civB, RelationshipType.TRADE_PARTNER);
                case TRIBUTARY_VASSALAGE -> relations.setRelationship(civA, civB, RelationshipType.ALLIED);
            }
            relations.adjustScore(civA, civB, 40);
        }

        return treaty;
    }

    public UltimatumRecord issueUltimatum(String senderCiv, String targetCiv, UltimatumDemand demand,
                                          int currentYear, int daysDeadline) {
        if (senderCiv == null || targetCiv == null || demand == null) return null;

        String id = "ultimatum_" + senderCiv + "_" + targetCiv + "_" + System.currentTimeMillis() % 10000;
        UltimatumRecord record = new UltimatumRecord(id, senderCiv, targetCiv, demand, currentYear + Math.max(1, daysDeadline / 365));
        pendingUltimatums.put(id, record);
        return record;
    }

    public boolean respondToUltimatum(String ultimatumId, boolean accept, WarEngine warEngine,
                                      FactionRelationshipSystem relations, WorldStateManager state) {
        UltimatumRecord record = pendingUltimatums.get(ultimatumId);
        if (record == null || record.isResolved()) return false;

        record.resolve(accept);
        if (accept) {
            if (relations != null) {
                relations.adjustScore(record.senderCiv(), record.targetCiv(), 15);
            }
            if (state != null) {
                state.setFlag("diplomacy.ultimatum_accepted." + ultimatumId, "true");
            }
        } else {
            // Rejection results in immediate casus belli declaration of war
            if (relations != null) {
                relations.setRelationship(record.senderCiv(), record.targetCiv(), RelationshipType.WAR);
                relations.adjustScore(record.senderCiv(), record.targetCiv(), -60);
            }
            if (warEngine != null) {
                warEngine.declareWar(record.senderCiv(), record.targetCiv(),
                    "Refusal of diplomatic ultimatum: " + record.demand().name(),
                    state != null ? state.getCurrentYear() : 0, state);
            }
        }
        return true;
    }

    public List<TreatyRecord> getTreatiesForCiv(String civId) {
        if (civId == null) return List.of();
        String c = civId.toLowerCase(Locale.ROOT);
        return activeTreaties.values().stream()
            .filter(t -> t.civA().equalsIgnoreCase(c) || t.civB().equalsIgnoreCase(c))
            .toList();
    }

    public List<TreatyRecord> getAllActiveTreaties() {
        return List.copyOf(activeTreaties.values());
    }

    public List<UltimatumRecord> getPendingUltimatums() {
        return pendingUltimatums.values().stream().filter(u -> !u.isResolved()).toList();
    }
}
