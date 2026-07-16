package com.eternalengine.villager;

import java.util.UUID;

/**
 * Virtualized villager/person state. Used when entities are far from players.
 */
public final class VirtualVillager {
    private final UUID entityUuid;
    private String civId;
    private String roleType;
    private String displayName;
    private double latitude;
    private double longitude;
    private int loyaltyScore;
    private int health;
    private boolean dead;
    private int lastUpdatedYear;

    public VirtualVillager(UUID entityUuid, String civId, String roleType, String displayName,
                           double latitude, double longitude, int loyaltyScore, int health, int lastUpdatedYear) {
        this.entityUuid = entityUuid == null ? UUID.randomUUID() : entityUuid;
        this.civId = civId == null || civId.isBlank() ? "neutral" : civId;
        this.roleType = roleType == null || roleType.isBlank() ? "VILLAGER" : roleType;
        this.displayName = displayName == null || displayName.isBlank() ? this.roleType : displayName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.loyaltyScore = clamp100(loyaltyScore);
        this.health = clamp100(health);
        this.lastUpdatedYear = lastUpdatedYear;
    }

    public void updateYear(int simYear) {
        if (dead) {
            return;
        }
        int delta = Math.max(0, simYear - lastUpdatedYear);
        if (delta > 0) {
            health = clamp100(health - Math.min(20, delta / 20));
            if (health <= 0) {
                dead = true;
            }
            lastUpdatedYear = simYear;
        }
    }

    private int clamp100(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public UUID entityUuid() { return entityUuid; }
    public String civId() { return civId; }
    public void setCivId(String civId) { this.civId = civId == null ? this.civId : civId; }
    public String roleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType == null ? this.roleType : roleType; }
    public String displayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? this.displayName : displayName; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public void setLocation(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
    public int loyaltyScore() { return loyaltyScore; }
    public void setLoyaltyScore(int loyaltyScore) { this.loyaltyScore = clamp100(loyaltyScore); }
    public int health() { return health; }
    public void setHealth(int health) { this.health = clamp100(health); }
    public boolean dead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }
    public int lastUpdatedYear() { return lastUpdatedYear; }
}
