package com.eternalengine.reputation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic action-to-reputation effect registry.
 *
 * This foundation contains the core actions needed by Phase 0 systems. Later
 * phases can extend this registry to the full 100+ action list without changing
 * ReputationEngine's application logic.
 */
public final class ActionReputationEffects {

    private static final Map<String, ReputationEffect> EFFECTS = new HashMap<>();

    static {
        register("WIN_MAJOR_BATTLE",
            pos(Map.of(ReputationDimension.MILITARY, 25, ReputationDimension.POLITICAL, 10, ReputationDimension.PERSONAL, 15)),
            neg(Map.of()),
            "Won a major battle");

        register("LOSE_MAJOR_BATTLE",
            pos(Map.of()),
            neg(Map.of(ReputationDimension.INCOMPETENCE, 20, ReputationDimension.COWARDICE, 15)),
            "Lost a major battle");

        register("ORDER_CIVILIAN_MASSACRE",
            pos(Map.of(ReputationDimension.MILITARY, 5)),
            neg(Map.of(ReputationDimension.CRUELTY, 50, ReputationDimension.INFAMY, 40,
                ReputationDimension.TYRANNY, 30, ReputationDimension.HERESY, 35)),
            "Ordered massacre of civilians");

        register("SHOW_MERCY_TO_ENEMY",
            pos(Map.of(ReputationDimension.PERSONAL, 20, ReputationDimension.RELIGIOUS, 15, ReputationDimension.CULTURAL, 10)),
            neg(Map.of()),
            "Showed mercy to an enemy");

        register("BETRAY_ALLY",
            pos(Map.of()),
            neg(Map.of(ReputationDimension.TREACHERY, 60, ReputationDimension.DISHONOR, 40)),
            "Betrayed an ally");

        register("FLEE_BATTLE",
            pos(Map.of()),
            neg(Map.of(ReputationDimension.COWARDICE, 50, ReputationDimension.DISHONOR, 30)),
            "Fled from battle");

        register("FEED_STARVING_POPULATION",
            pos(Map.of(ReputationDimension.PERSONAL, 25, ReputationDimension.POLITICAL, 15, ReputationDimension.RELIGIOUS, 10)),
            neg(Map.of()),
            "Fed a starving population");

        register("BUILD_HOSPITAL",
            pos(Map.of(ReputationDimension.CULTURAL, 15, ReputationDimension.PERSONAL, 10, ReputationDimension.POLITICAL, 8)),
            neg(Map.of()),
            "Built a hospital");

        register("GIVE_INSPIRING_SPEECH",
            pos(Map.of(ReputationDimension.PERSONAL, 10, ReputationDimension.POLITICAL, 8)),
            neg(Map.of()),
            "Gave an inspiring speech");

        register("LEGITIMATE_DUEL_VICTORY",
            pos(Map.of(ReputationDimension.MILITARY, 15, ReputationDimension.PERSONAL, 5)),
            neg(Map.of(ReputationDimension.INFAMY, 5)),
            "Defeated a VIP in a legitimate duel or battle");

        register("VIP_ASSASSINATION",
            pos(Map.of(ReputationDimension.POLITICAL, 5)),
            neg(Map.of(ReputationDimension.TREACHERY, 35, ReputationDimension.INFAMY, 25, ReputationDimension.CRUELTY, 15)),
            "Killed a VIP through assassination or murder");

        register("KILL_BELOVED_VIP",
            pos(Map.of()),
            neg(Map.of(ReputationDimension.INSTABILITY, 20, ReputationDimension.INFAMY, 10)),
            "Killed a beloved VIP, destabilizing loyalists");

        register("KILL_RELIGIOUS_VIP",
            pos(Map.of()),
            neg(Map.of(ReputationDimension.HERESY, 25, ReputationDimension.INFAMY, 10)),
            "Killed a religious VIP");
    }

    private ActionReputationEffects() {
    }

    public static ReputationEffect getEffect(String actionType) {
        if (actionType == null) {
            return ReputationEffect.of("UNKNOWN_ACTION", Map.of(), Map.of(), "Unknown action");
        }
        return EFFECTS.getOrDefault(actionType, ReputationEffect.of(actionType, Map.of(), Map.of(), "No effect registered"));
    }

    public static Map<String, ReputationEffect> allEffects() {
        return Map.copyOf(EFFECTS);
    }

    private static void register(String actionType,
                                 Map<ReputationDimension, Integer> positive,
                                 Map<ReputationDimension, Integer> negative,
                                 String description) {
        EFFECTS.put(actionType, ReputationEffect.of(actionType, positive, negative, description));
    }

    private static Map<ReputationDimension, Integer> pos(Map<ReputationDimension, Integer> input) {
        return enumCopy(input, ReputationPolarity.POSITIVE);
    }

    private static Map<ReputationDimension, Integer> neg(Map<ReputationDimension, Integer> input) {
        return enumCopy(input, ReputationPolarity.NEGATIVE);
    }

    private static Map<ReputationDimension, Integer> enumCopy(Map<ReputationDimension, Integer> input, ReputationPolarity polarity) {
        Map<ReputationDimension, Integer> copy = new EnumMap<>(ReputationDimension.class);
        for (Map.Entry<ReputationDimension, Integer> entry : input.entrySet()) {
            if (entry.getKey() != null && entry.getKey().polarity() == polarity) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
