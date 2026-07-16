package com.eternalengine.war;

/**
 * Immutable battle record.
 */
public final class BattleRecord {
    private final String battleId;
    private final String location;
    private final int year;
    private final int month;
    private final String attackerCivId;
    private final String defenderCivId;
    private final long attackerLosses;
    private final long defenderLosses;
    private final BattleOutcome outcome;

    public BattleRecord(String battleId, String location, int year, int month,
                        String attackerCivId, String defenderCivId,
                        long attackerLosses, long defenderLosses, BattleOutcome outcome) {
        this.battleId = battleId == null || battleId.isBlank() ? "battle_unknown" : battleId;
        this.location = location == null ? "unknown" : location;
        this.year = year;
        this.month = Math.max(1, Math.min(12, month));
        this.attackerCivId = attackerCivId == null ? "unknown" : attackerCivId;
        this.defenderCivId = defenderCivId == null ? "unknown" : defenderCivId;
        this.attackerLosses = Math.max(0, attackerLosses);
        this.defenderLosses = Math.max(0, defenderLosses);
        this.outcome = outcome == null ? BattleOutcome.DRAW : outcome;
    }

    public String battleId() { return battleId; }
    public String location() { return location; }
    public int year() { return year; }
    public int month() { return month; }
    public String attackerCivId() { return attackerCivId; }
    public String defenderCivId() { return defenderCivId; }
    public long attackerLosses() { return attackerLosses; }
    public long defenderLosses() { return defenderLosses; }
    public BattleOutcome outcome() { return outcome; }
}
