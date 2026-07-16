package com.eternalengine.war;

import com.eternalengine.incident.IncidentSeed;

/**
 * Deterministic battle simulator foundation.
 */
public final class BattleSimulator {

    public BattleResult simulate(Army attacker, Army defender, double terrainBonus, int techGap, int year) {
        if (attacker == null || defender == null) {
            return new BattleResult(BattleOutcome.DRAW, 0L, 0L, 0, 0, "Battle could not be simulated.");
        }
        double winProbability = calculateWinProbability(attacker, defender, terrainBonus, techGap);
        double roll = roll(attacker.armyId(), defender.armyId(), year);
        BattleOutcome outcome;
        if (Math.abs(roll - winProbability) < 0.08d) {
            outcome = BattleOutcome.DRAW;
        } else if (roll < winProbability) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
        } else {
            outcome = BattleOutcome.DEFENDER_VICTORY;
        }
        long attackerLosses = Math.round(attacker.size() * (outcome == BattleOutcome.ATTACKER_VICTORY ? 0.08d : 0.18d));
        long defenderLosses = Math.round(defender.size() * (outcome == BattleOutcome.DEFENDER_VICTORY ? 0.08d : 0.18d));
        int attackerMorale = outcome == BattleOutcome.ATTACKER_VICTORY ? 10 : outcome == BattleOutcome.DRAW ? -2 : -15;
        int defenderMorale = outcome == BattleOutcome.DEFENDER_VICTORY ? 10 : outcome == BattleOutcome.DRAW ? -2 : -15;
        return new BattleResult(outcome, attackerLosses, defenderLosses, attackerMorale, defenderMorale,
            "Battle outcome=" + outcome + " probability=" + winProbability + " roll=" + roll);
    }

    public double calculateWinProbability(Army attacker, Army defender, double terrainBonus, int techGap) {
        if (attacker == null || defender == null || attacker.size() <= 0 || defender.size() <= 0) {
            return 0.5d;
        }
        double sizeRatio = attacker.size() / (double) Math.max(1L, attacker.size() + defender.size());
        double morale = (attacker.morale() - defender.morale()) / 300.0d;
        double supply = (attacker.supplyLevel() - defender.supplyLevel()) / 300.0d;
        double tech = Math.max(-0.20d, Math.min(0.20d, techGap * 0.04d));
        double terrain = Math.max(-0.20d, Math.min(0.20d, terrainBonus));
        return clamp(sizeRatio + morale + supply + tech + terrain);
    }

    public void applyResult(BattleResult result, Army attacker, Army defender, War war, int year) {
        if (result == null || attacker == null || defender == null) {
            return;
        }
        attacker.setSize(attacker.size() - result.attackerLosses());
        defender.setSize(defender.size() - result.defenderLosses());
        attacker.adjustMorale(result.attackerMoraleChange());
        defender.adjustMorale(result.defenderMoraleChange());
        if (war != null) {
            war.addBattle(new BattleRecord("battle_" + IncidentSeed.stableHex(attacker.armyId(), defender.armyId(), year).substring(0, 12),
                "field", year, 1, attacker.ownerCivId(), defender.ownerCivId(),
                result.attackerLosses(), result.defenderLosses(), result.outcome()));
            war.increaseExhaustion((result.attackerLosses() + result.defenderLosses()) / 1000.0d);
        }
    }

    private double roll(String attackerId, String defenderId, int year) {
        long raw = IncidentSeed.stableLong(attackerId, defenderId, year, "battle");
        return ((raw & Long.MAX_VALUE) % 10_000L) / 10_000.0d;
    }

    private double clamp(double value) {
        return Math.max(0.05d, Math.min(0.95d, value));
    }

    public record BattleResult(BattleOutcome outcome, long attackerLosses, long defenderLosses,
                               int attackerMoraleChange, int defenderMoraleChange, String narrativeDesc) {
    }
}
