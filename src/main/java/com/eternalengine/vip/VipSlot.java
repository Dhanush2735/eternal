package com.eternalengine.vip;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * VIP slot definition/state.
 */
public final class VipSlot {
    private final String vipId;
    private String name;
    private int birthYear;
    private int historicalDeathYear;
    private String historicalDeathCause;
    private String civilizationId;
    private VipRoleType roleType;
    private double spawnLat;
    private double spawnLon;
    private int authorityLevel;
    private final List<String> commandsAvailable = new ArrayList<>();
    private final Map<String, Integer> startingResources = new HashMap<>();
    private final List<String> preconditionIds = new ArrayList<>();
    private final Map<String, Integer> personality = new HashMap<>();
    private final List<String> historicalEventIds = new ArrayList<>();
    private final List<String> successorVipIds = new ArrayList<>();
    private final Map<String, Integer> repStartPositive = new HashMap<>();
    private final Map<String, Integer> repStartNegative = new HashMap<>();
    private UUID assignedPlayerUuid;
    private boolean dead;
    private int actualDeathYear;
    private String actualDeathCause;
    private int age;
    private int health;

    public VipSlot(String vipId, String name, int birthYear, int historicalDeathYear,
                   String civilizationId, VipRoleType roleType, double spawnLat, double spawnLon,
                   int authorityLevel) {
        this.vipId = vipId == null || vipId.isBlank() ? "vip_unknown" : vipId;
        this.name = name == null || name.isBlank() ? this.vipId : name;
        this.birthYear = birthYear;
        this.historicalDeathYear = historicalDeathYear;
        this.historicalDeathCause = "historical";
        this.civilizationId = civilizationId == null || civilizationId.isBlank() ? "neutral" : civilizationId;
        this.roleType = roleType == null ? VipRoleType.POLITICAL : roleType;
        this.spawnLat = spawnLat;
        this.spawnLon = spawnLon;
        this.authorityLevel = Math.max(0, Math.min(10, authorityLevel));
        this.health = 100;
        this.actualDeathYear = Integer.MIN_VALUE;
    }

    public boolean isAvailable(int simYear) {
        return !dead && assignedPlayerUuid == null && simYear >= birthYear && simYear <= historicalDeathYear;
    }

    public boolean isClaimedByPlayer() {
        return assignedPlayerUuid != null;
    }

    public Optional<ServerPlayer> getAssignedPlayer(MinecraftServer server) {
        if (server == null || assignedPlayerUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(assignedPlayerUuid));
    }

    public void assign(UUID playerUuid) {
        this.assignedPlayerUuid = playerUuid;
    }

    public void release() {
        this.assignedPlayerUuid = null;
    }

    public void markDead(String cause, int year) {
        this.dead = true;
        this.actualDeathCause = cause == null ? "unknown" : cause;
        this.actualDeathYear = year;
        this.assignedPlayerUuid = null;
    }

    public void updateAge(int simYear) {
        this.age = Math.max(0, simYear - birthYear);
    }

    public String vipId() { return vipId; }
    public String name() { return name; }
    public void setName(String name) { this.name = name == null ? this.name : name; }
    public int birthYear() { return birthYear; }
    public void setBirthYear(int birthYear) { this.birthYear = birthYear; }
    public int historicalDeathYear() { return historicalDeathYear; }
    public void setHistoricalDeathYear(int historicalDeathYear) { this.historicalDeathYear = historicalDeathYear; }
    public String historicalDeathCause() { return historicalDeathCause; }
    public void setHistoricalDeathCause(String historicalDeathCause) { this.historicalDeathCause = historicalDeathCause == null ? this.historicalDeathCause : historicalDeathCause; }
    public String civilizationId() { return civilizationId; }
    public void setCivilizationId(String civilizationId) { this.civilizationId = civilizationId == null ? this.civilizationId : civilizationId; }
    public VipRoleType roleType() { return roleType; }
    public void setRoleType(VipRoleType roleType) { this.roleType = roleType == null ? this.roleType : roleType; }
    public double spawnLat() { return spawnLat; }
    public double spawnLon() { return spawnLon; }
    public void setSpawn(double lat, double lon) { this.spawnLat = lat; this.spawnLon = lon; }
    public int authorityLevel() { return authorityLevel; }
    public void setAuthorityLevel(int authorityLevel) { this.authorityLevel = Math.max(0, Math.min(10, authorityLevel)); }
    public List<String> commandsAvailable() { return List.copyOf(commandsAvailable); }
    public void addCommand(String command) { if (command != null && !commandsAvailable.contains(command)) commandsAvailable.add(command); }
    public Map<String, Integer> startingResources() { return Map.copyOf(startingResources); }
    public void putStartingResource(String key, int value) { if (key != null) startingResources.put(key, value); }
    public List<String> preconditionIds() { return List.copyOf(preconditionIds); }
    public void addPrecondition(String id) { if (id != null) preconditionIds.add(id); }
    public Map<String, Integer> personality() { return Map.copyOf(personality); }
    public void putPersonality(String key, int value) { if (key != null) personality.put(key, Math.max(0, Math.min(100, value))); }
    public List<String> historicalEventIds() { return List.copyOf(historicalEventIds); }
    public void addHistoricalEventId(String id) { if (id != null) historicalEventIds.add(id); }
    public List<String> successorVipIds() { return List.copyOf(successorVipIds); }
    public void addSuccessorVipId(String id) { if (id != null) successorVipIds.add(id); }
    public Map<String, Integer> repStartPositive() { return Map.copyOf(repStartPositive); }
    public Map<String, Integer> repStartNegative() { return Map.copyOf(repStartNegative); }
    public UUID assignedPlayerUuid() { return assignedPlayerUuid; }
    public boolean dead() { return dead; }
    public int actualDeathYear() { return actualDeathYear; }
    public String actualDeathCause() { return actualDeathCause; }
    public int age() { return age; }
    public int health() { return health; }
    public void setHealth(int health) { this.health = Math.max(0, Math.min(100, health)); }
}
