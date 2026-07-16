package com.eternalengine.vip;

import java.util.concurrent.ConcurrentHashMap;

/**
 * VIP authority and loyalty foundation.
 */
public final class AuthoritySystem {
    private final ConcurrentHashMap<String, Integer> loyaltyScores = new ConcurrentHashMap<>();

    public int getLoyaltyScore(String entityId, String vipId) {
        return loyaltyScores.getOrDefault(key(entityId, vipId), 50);
    }

    public boolean willFollowOrder(String entityId, VipSlot vip, String commandType) {
        if (vip == null) {
            return false;
        }
        int loyalty = getLoyaltyScore(entityId, vip.vipId());
        int difficulty = commandDifficulty(commandType);
        return loyalty + vip.authorityLevel() * 5 >= difficulty;
    }

    public void handleRefusal(String entityId, VipSlot vip, String commandType) {
        if (vip != null) {
            updateLoyalty(entityId, vip, "REFUSED_ORDER");
        }
    }

    public void updateLoyalty(String entityId, VipSlot vip, String loyaltyEvent) {
        if (vip == null || entityId == null) {
            return;
        }
        int delta = switch (loyaltyEvent == null ? "" : loyaltyEvent) {
            case "VICTORY_IN_BATTLE" -> 10;
            case "DEFEAT_IN_BATTLE" -> -15;
            case "PAID_ON_TIME" -> 5;
            case "NOT_PAID" -> -20;
            case "SPEECH_GIVEN" -> 3;
            case "ATROCITY_ORDERED" -> -30;
            case "SUCCESSFUL_CAMPAIGN" -> 20;
            case "TERRITORY_LOST" -> -25;
            case "FAMINE_OCCURRED" -> -15;
            case "PERSONAL_FAVOR" -> 8;
            case "REFUSED_ORDER" -> -5;
            default -> 0;
        };
        String key = key(entityId, vip.vipId());
        loyaltyScores.put(key, Math.max(0, Math.min(100, getLoyaltyScore(entityId, vip.vipId()) + delta)));
    }

    private int commandDifficulty(String commandType) {
        return switch (commandType == null ? "" : commandType) {
            case "RETREAT", "MOVE_ARMY" -> 35;
            case "ATTACK_TERRITORY", "SIEGE" -> 55;
            case "DECLARE_WAR", "CALL_HOLY_WAR" -> 65;
            case "ORDER_ATROCITY" -> 90;
            default -> 50;
        };
    }

    private String key(String entityId, String vipId) {
        return (entityId == null ? "unknown_entity" : entityId) + ":" + (vipId == null ? "unknown_vip" : vipId);
    }
}
