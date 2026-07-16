package com.eternalengine.vip;

import java.util.Objects;

/**
 * Minimal VIP bot data needed for takeover validation.
 * Full VIP system can later replace this with database-backed VipSlot records.
 */
public final class VipBotRecord {

    private final String vipId;
    private final String name;
    private final int tier;
    private final boolean claimable;
    private final boolean beloved;
    private final boolean religious;
    private boolean alive;
    private String assignedPlayerUuid;

    public VipBotRecord(String vipId, String name, int tier, boolean alive,
                        boolean claimable, boolean beloved, boolean religious) {
        this.vipId = Objects.requireNonNullElse(vipId, "unknown_vip");
        this.name = Objects.requireNonNullElse(name, this.vipId);
        this.tier = Math.max(1, Math.min(3, tier));
        this.alive = alive;
        this.claimable = claimable;
        this.beloved = beloved;
        this.religious = religious;
    }

    public String vipId() { return vipId; }
    public String name() { return name; }
    public int tier() { return tier; }
    public boolean alive() { return alive; }
    public boolean claimable() { return claimable; }
    public boolean beloved() { return beloved; }
    public boolean religious() { return religious; }
    public String assignedPlayerUuid() { return assignedPlayerUuid; }

    void markTaken(String playerUuid) {
        this.alive = false;
        this.assignedPlayerUuid = playerUuid;
    }
}
