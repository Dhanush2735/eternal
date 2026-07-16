package com.eternalengine.vip;

/**
 * Input facts for VIP takeover after a player kills a VIP bot.
 */
public final class VipTakeoverContext {

    private final String killerPlayerUuid;
    private final String killedVipId;
    private final VipTakeoverMethod method;
    private final boolean adminApproved;
    private final int currentYear;

    public VipTakeoverContext(String killerPlayerUuid, String killedVipId,
                              VipTakeoverMethod method, boolean adminApproved, int currentYear) {
        this.killerPlayerUuid = killerPlayerUuid == null ? "" : killerPlayerUuid;
        this.killedVipId = killedVipId == null ? "" : killedVipId;
        this.method = method == null ? VipTakeoverMethod.UNKNOWN : method;
        this.adminApproved = adminApproved;
        this.currentYear = currentYear;
    }

    public String killerPlayerUuid() { return killerPlayerUuid; }
    public String killedVipId() { return killedVipId; }
    public VipTakeoverMethod method() { return method; }
    public boolean adminApproved() { return adminApproved; }
    public int currentYear() { return currentYear; }
}
