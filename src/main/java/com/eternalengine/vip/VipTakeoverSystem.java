package com.eternalengine.vip;

import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.reputation.ReputationEngine;

/**
 * Server-side VIP takeover validation.
 *
 * A player may take over a VIP bot only through this validated pathway. Later
 * entity death event handlers should feed this system with verified kill facts.
 */
public final class VipTakeoverSystem {

    private final ReputationEngine reputationEngine;

    public VipTakeoverSystem() {
        this(new ReputationEngine());
    }

    public VipTakeoverSystem(ReputationEngine reputationEngine) {
        this.reputationEngine = reputationEngine == null ? new ReputationEngine() : reputationEngine;
    }

    public VipTakeoverResult attemptTakeover(IPlayerSimulationData playerData,
                                             String playerUuid,
                                             VipBotRecord vip,
                                             VipTakeoverContext context) {
        if (playerData == null) {
            return VipTakeoverResult.failure("missing player simulation data");
        }
        if (playerUuid == null || playerUuid.isBlank()) {
            return VipTakeoverResult.failure("missing player UUID");
        }
        if (vip == null) {
            return VipTakeoverResult.failure("missing VIP record");
        }
        if (context == null) {
            return VipTakeoverResult.failure("missing takeover context");
        }
        if (!playerUuid.equals(context.killerPlayerUuid())) {
            return VipTakeoverResult.failure("killer UUID does not match player");
        }
        if (!vip.vipId().equals(context.killedVipId())) {
            return VipTakeoverResult.failure("killed VIP ID does not match VIP record");
        }
        if (playerData.isVip()) {
            return VipTakeoverResult.failure("player already controls a VIP");
        }
        if (!vip.alive()) {
            return VipTakeoverResult.failure("VIP is already dead");
        }
        if (!vip.claimable()) {
            return VipTakeoverResult.failure("VIP is not claimable");
        }
        if (vip.tier() <= 1 && !context.adminApproved()) {
            return VipTakeoverResult.failure("tier-1 VIP takeover requires admin approval");
        }

        String action = context.method().isLegitimateCombat()
            ? "LEGITIMATE_DUEL_VICTORY"
            : "VIP_ASSASSINATION";

        reputationEngine.initializeDefaults(playerData);
        reputationEngine.applyAction(playerData, action);
        if (vip.beloved()) {
            reputationEngine.applyAction(playerData, "KILL_BELOVED_VIP");
        }
        if (vip.religious()) {
            reputationEngine.applyAction(playerData, "KILL_RELIGIOUS_VIP");
        }

        playerData.setVipId(vip.vipId());
        playerData.recordAction("VIP_TAKEOVER", context.currentYear(),
            "Took over " + vip.name() + " via " + context.method().name());
        vip.markTaken(playerUuid);

        return VipTakeoverResult.success("player took over VIP " + vip.vipId(), action);
    }
}
