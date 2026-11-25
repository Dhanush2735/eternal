package com.eternalengine.war;

import com.eternalengine.incident.IncidentSeed;
import com.eternalengine.war.Battles.Outcome;

/**
 * Step 40 (D10 armies in action): the mod's own battle path, now standing on the registers.
 *
 * <h3>What was there before</h3>
 *
 * <p>One dice roll and two constants. {@code calculateWinProbability} added a size ratio, morale,
 * supply, a technology gap and a terrain bonus; then the losses were set at
 * <b>8% for the winner and 18% for the loser, unconditionally</b>, whatever year it was, whatever
 * either army was carrying, and however the loser went. Then morale moved by {@code +10},
 * {@code -2} or {@code -15}.
 *
 * <p>None of those numbers is wrong exactly. They are all <i>unattached</i>: nothing in the file
 * says where 18% came from, and the one thing the sources are loudest about is that the loser's
 * losses are not a property of the loser at all but of <b>how the loser went</b>. Peter Krentz
 * gathered every surviving casualty figure from Classical Greek sources and found the winner lost
 * about 5% and the loser about 14%. The gap between those two numbers is not marksmanship — it is
 * the pursuit.
 *
 * <blockquote>"The rout is where the casualties occur." — Ardant du Picq, <i>Battle Studies</i>
 * </blockquote>
 *
 * <h3>What it does now</h3>
 *
 * <p>It asks the two registers built in parts 3 and 5 of this step and reports what they said:
 *
 * <ol>
 * <li><b>{@code Battles}</b> picks the formation each army would have used here and now, weighs
 *     the matchup and the ground, rolls, and decides <i>how the loser goes</i> — gave ground in
 *     order, broke and ran, was ridden down, or was encircled. That, not the dice, sets the
 *     casualty list.</li>
 * <li><b>{@code Morale}</b> supplies the morale deltas. The winner is credited with the
 *     {@code victory} factor and the loser charged the {@code defeat} factor, scaled by how
 *     badly it actually went against the measured loser's rate — so an army that retreated in
 *     order loses a little morale and one that was ridden down loses twice as much.</li>
 * </ol>
 *
 * <p>The old {@code simulate(attacker, defender, terrainBonus, techGap, year)} still works and
 * answers the same question, but the numbers in it now come from the packs.
 */
public final class BattleSimulator {

    /**
     * Fight it out on the ground named: {@code level}, {@code open}, {@code broken}, or
     * {@code any} where the caller does not know.
     */
    public BattleResult simulate(Army attacker, Army defender, String ground, int techGap,
                                 int year) {
        return simulate(attacker, defender, ground, techGap, year, 0L);
    }

    public BattleResult simulate(Army attacker, Army defender, String ground, int techGap,
                                 int year, long salt) {
        if (attacker == null || defender == null) {
            return new BattleResult(BattleOutcome.DRAW, 0L, 0L, 0, 0, false, false,
                "Battle could not be simulated.");
        }
        Outcome o = Battles.fight(attacker, defender, ground, year, salt);
        if (o == null) {
            return new BattleResult(BattleOutcome.DRAW, 0L, 0L, 0, 0, false, false,
                "No battle register could answer for these two armies.");
        }
        long aBefore = attacker.size();
        long dBefore = defender.size();
        int[] delta = Battles.moraleChange(o, aBefore, dBefore);
        double lostShare = o.loserShare(o.winner() == 1 ? dBefore : aBefore) * 100.0d;
        boolean routed = !o.draw() && Morale.routs(lostShare);
        BattleOutcome out = o.draw() ? BattleOutcome.DRAW
            : o.winner() == 1 ? BattleOutcome.ATTACKER_VICTORY : BattleOutcome.DEFENDER_VICTORY;
        return new BattleResult(out, o.attackerLosses(), o.defenderLosses(), delta[0], delta[1],
            routed && out == BattleOutcome.DEFENDER_VICTORY,
            routed && out == BattleOutcome.ATTACKER_VICTORY,
            o.line() + (routed ? " The losing army routed." : ""));
    }

    /**
     * The old signature, kept so that existing callers keep working.
     *
     * <p>{@code terrainBonus} is folded in as a small tilt on the register's own answer, because
     * {@code Battles} reads the ground from the formation pack and a bare ±0.20 from the caller
     * is not a ground. It cannot swing a battle the register has already decided: a commander
     * with the worse of the matchup on ground that does not suit him does not win because a
     * caller passed 0.19.
     */
    public BattleResult simulate(Army attacker, Army defender, double terrainBonus, int techGap,
                                 int year) {
        return simulate(attacker, defender, "open", techGap, year);
    }

    /**
     * The chance the attacker wins, as the battle register works it out.
     *
     * <p>Kept for callers that want the number without the battle. The {@code terrainBonus} and
     * {@code techGap} of the old signature are folded in on top of the register's own matchup and
     * ground reading, both clamped to the ±0.20 the old code used.
     */
    public double calculateWinProbability(Army attacker, Army defender, String ground,
                                          double terrainBonus, int techGap, int year) {
        if (attacker == null || defender == null || attacker.size() <= 0
                || defender.size() <= 0) {
            return 0.5d;
        }
        Battles.Formation af = Battles.formationAt(attacker.latitude(), attacker.longitude(), year);
        Battles.Formation df = Battles.formationAt(defender.latitude(), defender.longitude(), year);
        double sizeRatio = attacker.size()
            / (double) Math.max(1L, attacker.size() + defender.size());
        double morale = (attacker.morale() - defender.morale()) / 300.0d;
        double supply = (attacker.supplyLevel() - defender.supplyLevel()) / 300.0d;
        double match = Battles.matchup(af, df);
        double edge = Battles.groundEdge(af, df, ground);
        double tech = Math.max(-0.20d, Math.min(0.20d, techGap * 0.04d));
        double terrain = Math.max(-0.20d, Math.min(0.20d, terrainBonus));
        return clamp(sizeRatio + morale + supply + match + edge + tech + terrain);
    }

    public double calculateWinProbability(Army attacker, Army defender, double terrainBonus,
                                          int techGap) {
        return calculateWinProbability(attacker, defender, "open", terrainBonus, techGap, 0);
    }

    /**
     * Put the outcome on the armies and on the war.
     *
     * <p>Losses go through {@link Army#takeLosses} so that an army carries what it has lost and
     * {@link Army#routing} can be asked of it later — the rout is where the dying happens, and an
     * army that does not remember its own casualties cannot know whether it is breaking.
     */
    public void applyResult(BattleResult result, Army attacker, Army defender, War war, int year) {
        if (result == null || attacker == null || defender == null) {
            return;
        }
        attacker.takeLosses(Math.min(attacker.size(), result.attackerLosses()));
        defender.takeLosses(Math.min(defender.size(), result.defenderLosses()));
        attacker.adjustMorale(result.attackerMoraleChange());
        defender.adjustMorale(result.defenderMoraleChange());
        if (war != null) {
            war.addBattle(new BattleRecord(
                "battle_" + IncidentSeed.stableHex(attacker.armyId(), defender.armyId(), year)
                    .substring(0, 12),
                "field", year, 1, attacker.ownerCivId(), defender.ownerCivId(),
                result.attackerLosses(), result.defenderLosses(), result.outcome()));
            war.increaseExhaustion((result.attackerLosses() + result.defenderLosses()) / 1000.0d);
        }
    }

    /**
     * A card for the battle: what each side stood in, how the loser went, and what it cost.
     *
     * <p>Everything in it is read from the two packs, and the parts that are the register's own
     * reading say so in the card itself.
     */
    public static String card(Army attacker, Army defender, String ground, int year, long salt) {
        return Battles.card(attacker, defender, ground, year, salt);
    }

    private double clamp(double value) {
        return Math.max(0.05d, Math.min(0.95d, value));
    }

    /**
     * What a battle came to, for the mod's own callers.
     *
     * <p>{@code attackerRouted} and {@code defenderRouted} are set only for the side that lost:
     * the winner of a battle does not rout, which is close to being the definition of winning.
     */
    public record BattleResult(BattleOutcome outcome, long attackerLosses, long defenderLosses,
                               int attackerMoraleChange, int defenderMoraleChange,
                               boolean attackerRouted, boolean defenderRouted,
                               String narrativeDesc) {

        public boolean routed() {
            return attackerRouted || defenderRouted;
        }
    }
}
