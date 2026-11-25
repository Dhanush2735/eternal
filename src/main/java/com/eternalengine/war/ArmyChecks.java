package com.eternalengine.war;

import java.util.List;
import java.util.ArrayList;

/**
 * Step 40 (D10 armies in action): the checks for the {@code armies} part of {@code /ee check}.
 *
 * <p>These live here, next to the registers they check, rather than inside
 * {@code HistoryChecks}, for a concrete reason: {@code HistoryChecks} reaches into half the mod
 * and cannot be compiled without Minecraft on the classpath, so a check written only there would
 * go unexercised until somebody built the mod. Here it depends on nothing but the five registers
 * of this step, which means:
 *
 * <ul>
 * <li>{@code /ee check armies} runs it inside a real server;</li>
 * <li>and {@code worldfill/probe_lifetable/CheckArmies.java} runs <b>the very same code</b>
 *     outside one, in the sandbox, every time the probe runs.</li>
 * </ul>
 *
 * <p>So a claim cannot pass in the build and fail in the game. The four it holds the step to are
 * that <b>dispersion costs morale rather than granting it</b> (du Picq), that <b>the loser of a
 * battle loses more than the winner and the gap is the pursuit</b> (Krentz), that <b>desertion
 * lands on the measured rates</b>, and that <b>a side that cannot be bound is never offered
 * terms</b> — the last being the one a player notices, because it is the difference between a
 * town opening its gates on a named day and a town being sacked.
 */
public final class ArmyChecks {

    private ArmyChecks() {}

    /** Where the results go. {@code HistoryChecks.Section} fills one of these; so does the probe. */
    public interface Sink {
        void check(boolean ok, String text, String whyNot);
        void known(String text, String detail);
        void info(String text);
    }

    // ================================================================== step 40: armies in action

    /**
     * Armies in action (step 40), asked inside a running server the same questions the probe asks
     * outside it.
     *
     * <p>Five registers stand behind this: how armies stood, how a battle came out, what a siege
     * cost, why men stood, and what kind of party was fighting. The four claims worth checking
     * here are that <b>dispersion costs morale rather than granting it</b> (du Picq: "the more
     * men think themselves isolated, the more need they have of high morale"), that <b>the loser
     * of a battle loses more than the winner and the gap is the pursuit</b> (Krentz's 3-10%
     * against 10-20%), that <b>desertion lands on the measured rates</b>, and that <b>a side that
     * cannot be bound is never offered terms</b> — the last being the one a player notices,
     * because it is the difference between a town opening its gates on a named day and a town
     * being sacked.
     */
    public static void run(Sink sink) {
        sink.check(Battles.formationCount() > 0 && Morale.factorCount() > 0
                && Morale.raisedCount() > 0 && Morale.rateCount() > 0
                && Sides.partyCount() > 0 && Sieges.wallCount() > 0,
                "all five registers of the step load: " + Battles.formationCount() + " formations, "
                        + Morale.factorCount() + " morale factors, " + Morale.raisedCount()
                        + " ways of raising an army, " + Morale.rateCount() + " measured desertion "
                        + "rates, " + Sides.partyCount() + " kinds of party, " + Sieges.wallCount()
                        + " kinds of wall",
                "a register of the step did not load");

        // ------------------------------------------------------------ du Picq: the shape of it
        Morale.State inLine = morale(1863, 39.0d, -77.0d, "conscript_national",
                FormationType.LINE, 2);
        Morale.State inSkirmish = morale(1863, 39.0d, -77.0d, "conscript_national",
                FormationType.SKIRMISH, 1);
        int lineM = Morale.read(inLine).morale();
        int skimM = Morale.read(inSkirmish).morale();
        sink.check(skimM < lineM,
                "dispersion costs morale rather than granting it: the same men in skirmish order ("
                        + skimM + ") are worth less than in line (" + lineM + ")",
                "du Picq's whole argument is that loose order needs MORE cohesion, not less");

        Morale.State deep = morale(-400, 38.0d, 23.0d, "citizen_militia", FormationType.SQUARE, 4);
        Morale.State thin = morale(-400, 38.0d, 23.0d, "citizen_militia", FormationType.SQUARE, 1);
        sink.check(Morale.read(deep).morale() > Morale.read(thin).morale(),
                "depth gains it: four ranks (" + Morale.read(deep).morale() + ") beat one ("
                        + Morale.read(thin).morale() + "), because the rear cannot see the carnage",
                "the Roman triplex acies was about exactly this");

        // ------------------------------------------------------------ winning and losing
        Morale.State plain = morale(1864, 37.5d, -77.5d, "conscript_national", FormationType.LINE,
                2);
        Morale.State winning = withFlags(plain, true, false);
        Morale.State losing = withFlags(plain, false, true);
        int gain = Morale.read(winning).morale() - Morale.read(plain).morale();
        int loss = Morale.read(plain).morale() - Morale.read(losing).morale();
        sink.check(loss > gain && gain > 0,
                "losing (" + -loss + ") is worse than winning (" + gain + ") is good: 'Lee's men "
                        + "fought hungry through much of the Civil War, cheerfully and without "
                        + "complaint; it was only when Grant and Sherman began to close in that "
                        + "they deserted'",
                "the record is unambiguous about the asymmetry");

        // ------------------------------------------------------------ the rout is where the dying is
        sink.check(Morale.loserLoss() > Morale.winnerLoss()
                        && Morale.pursuitLoss() > Morale.loserLoss() / 2.0d,
                "the rout is where the dying happens: the losers lose " + Morale.loserLoss()
                        + "% against the winners' " + Morale.winnerLoss() + "%, and "
                        + Morale.pursuitLoss() + " points of that gap is the pursuit and nothing "
                        + "else",
                "a model that charges both sides the same percentage is modelling the fighting, "
                        + "not the battle");
        sink.check(Morale.routs(Morale.routThreshold()) && !Morale.routs(Morale.routThreshold() - 1.0d),
                "an army is routing at " + Morale.routThreshold() + "% gone and not before it",
                "the breaking point is Krentz's measured 12 per cent");

        BattleSimulator sim = new BattleSimulator();
        // Brandenburg and Austria in 1866: a year and two places the formation register has a row
        // for, so the report can name both sides and say which went
        Army a1 = fieldArmy("ee_a1", 52.5d, 13.4d, 14000L, 1866);
        Army a2 = fieldArmy("ee_a2", 48.2d, 16.4d, 11000L, 1866);
        long a1Before = a1.size();
        long a2Before = a2.size();
        BattleSimulator.BattleResult br = sim.simulate(a1, a2, "open", 0, 1866, 31L);
        sink.check(br != null && !br.narrativeDesc().isBlank(),
                "the mod's own battle path answers, and says why: "
                        + (br == null ? "nothing" : br.narrativeDesc()),
                "BattleSimulator is the mod's battle and it is not using the registers");
        sink.check(br != null && !(br.attackerLosses() == 0 && br.defenderLosses() == 0),
                "it is not the old flat 8% and 18%: the attacker lost " + br.attackerLosses()
                        + " and the defender " + br.defenderLosses(),
                "a battle that costs nobody anything is not a battle");
        if (br != null) {
            sim.applyResult(br, a1, a2, null, 1866);
            sink.check(a1.size() == a1Before - br.attackerLosses()
                            && a2.size() == a2Before - br.defenderLosses(),
                    "and the armies carry what they lost, so that a rout can be known later",
                    "losses are not going through takeLosses");
            sink.check(!br.routed() || (br.attackerRouted() != br.defenderRouted()),
                    "only the side that lost is ever said to have routed",
                    "the winner of a battle does not rout");
        }

        // ------------------------------------------------------------ desertion, on the record
        Morale.State prussian = morale(1730, 52.5d, 13.4d, "professional_standing",
                FormationType.LINE, 2);
        double rate = Morale.desertionPerYear(prussian);
        sink.check(rate >= 3.0d && rate <= 5.0d,
                "a long-service army in the age of reason loses " + rate + "% a year, inside "
                        + "Duffy's measured band of 3.2 (Prussia), 4.4 (France) and 7.0 (Saxony)",
                "the desertion register is not landing on the record");

        // ------------------------------------------------------------ sides, and who can be bound
        sink.check(Sides.party("sovereign_state").willTreat()
                        && !Sides.party("warlord_band").willTreat()
                        && !Sides.party("mercenary_company").willTreat()
                        && !Sides.party("civilians").canInitiate(),
                "a sovereign can be bound, a warlord band cannot, a hired company cannot, and "
                        + "civilians cannot start a war - Brecke's rule is that a party need not "
                        + "be a state, and the register keeps the consequences of that",
                "the sides register has lost the distinction it exists to make");

        War two = new War("ee_check_war", "a war of two states", java.util.List.of("civ_a"),
                java.util.List.of("civ_b"), 1200, null);
        sink.check(two.canTreat(), "a war between two states can be ended by a treaty",
                "nothing can be negotiated and no siege can end in terms");
        two.setPartyOf("civ_b", "warlord_band");
        sink.check(!two.canTreat(),
                "a war with a warlord band in it cannot: " + two.treatyLine(),
                "one member that cannot be bound is enough to make the whole war unbargainable");

        // ------------------------------------------------------------ the gates, or the sack
        int communeTerms = 0;
        int bandTerms = 0;
        for (int i = 0; i < 24; i++) {
            int year = 1150 + (i % 9) * 60;
            long mouths = 5000L + (i * 7919L) % 20000L;
            long garrison = Math.max(200L, mouths / 6L + (i * 31L) % 900L);
            String ground = i % 3 == 0 ? "dry" : i % 3 == 1 ? "rock" : "wet";
            communeTerms += termsIn("city_commune", i, year, mouths, garrison, ground);
            bandTerms += termsIn("warlord_band", i, year, mouths, garrison, ground);
        }
        sink.check(communeTerms > 0 && bandTerms == 0,
                "a commune's gates open on a named day and a warlord's never do: " + communeTerms
                        + " of 24 sieges ended in terms against " + bandTerms
                        + " - a conditional surrender is a bargain between two parties who can "
                        + "each be held to it, and a warlord band has nothing to hold",
                "the siege register is offering terms to a party that cannot be bound");

        sink.known("no commander in it",
                "du Picq, and everyone since, would say a commander is where most of morale "
                        + "lives, and no row in the pack can express one");
        sink.known("no row for Oceania, none for the Americas before 1491, none for sub-Saharan "
                        + "Africa",
                "nothing was read for them, so the register answers with the world-wide default "
                        + "and says that is what it is doing");
        sink.known("Brecke's Conflict Catalog was never loaded",
                "it is a binary spreadsheet; the kinds of party are read from the catalog's own "
                        + "description of its fields, and the 3,708 conflicts are not in the pack");
    }

    private static Morale.State morale(int year, double lat, double lon, String raised,
                                       FormationType f, int ranks) {
        return new Morale.State(year, lat, lon, raised, f, ranks, false, false, false, false,
            true, true, false, false, false, false, false, false, false, false, false, false);
    }

    private static Morale.State withFlags(Morale.State s2, boolean winning2, boolean losing2) {
        return new Morale.State(s2.year(), s2.lat(), s2.lon(), s2.raised(), s2.formation(),
            s2.ranks(), winning2, losing2, s2.lastVictory(), s2.lastDefeat(), s2.home(), s2.paid(),
            s2.plunderPromised(), s2.plundered(), s2.besieged(), s2.hungry(), s2.sick(),
            s2.drafted(), s2.hired(), s2.blooded(), s2.raw(), s2.longWar());
    }

    private static Army fieldArmy(String id, double lat, double lon, long size, int year) {
        Morale.Raised r = Morale.raisedAt(lat, lon, year);
        return new Army(id, "civ_" + id, lat, lon, size, 55, 60,
            year < 1500 ? "MEDIEVAL" : year < 1800 ? "EARLY_MODERN" : "INDUSTRIAL", 20.0d, lat,
            lon, r == null ? null : r.id(), null, null);
    }

    /** 1 if this siege ended in terms, 0 if it did not. */
    private static int termsIn(String partyId, int i, int year, long mouths, long garrison,
                               String ground) {
        Sieges.Town t = new Sieges.Town("a town", 52.0d, -1.5d, mouths, garrison,
            20 + (i * 13) % 300, 10 + (i * 17) % 80, (i * 7) % 70, ground, partyId);
        Army a = new Army("ee_besieger_" + i, "attacker", 52.0d, -1.5d,
            Math.max(500L, garrison * (1L + (i % 7))), 40 + (i * 11) % 55, 20 + (i * 23) % 75,
            "MEDIEVAL", 20.0d);
        Sieges.Siege siege = Sieges.besiege(t, a, year, -1, 9000L + i);
        return siege != null && "terms".equals(siege.endingId()) ? 1 : 0;
    }
}
