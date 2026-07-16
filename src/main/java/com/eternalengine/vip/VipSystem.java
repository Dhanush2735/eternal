package com.eternalengine.vip;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.reputation.ReputationDimension;
import com.eternalengine.reputation.ReputationEngine;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VIP slot registry and claim/release foundation.
 */
public final class VipSystem {
    private final ConcurrentHashMap<String, VipSlot> vipDatabase = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerToVip = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> vipToPlayer = new ConcurrentHashMap<>();
    private final ReputationEngine reputationEngine;

    public VipSystem(ReputationEngine reputationEngine) {
        this.reputationEngine = reputationEngine == null ? new ReputationEngine() : reputationEngine;
        registerDefaults();
    }

    public void loadVips(Path dataDir) {
        if (dataDir == null || !Files.exists(dataDir)) {
            return;
        }
        try (var stream = Files.walk(dataDir)) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(this::loadVipFile);
        } catch (Exception ignored) {
            // Keep defaults on load failure.
        }
    }

    public List<VipSlot> getAvailableVips(int simYear, WorldStateManager state, MinecraftServer server) {
        List<VipSlot> result = new ArrayList<>();
        for (VipSlot slot : vipDatabase.values()) {
            if (slot.isAvailable(simYear) && preconditionsMet(slot, state)) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    public boolean claimVip(ServerPlayer player, String vipId, int simYear) {
        if (player == null || vipId == null) {
            return false;
        }
        VipSlot slot = vipDatabase.get(vipId);
        if (slot == null || !slot.isAvailable(simYear) || playerToVip.containsKey(player.getUUID())) {
            return false;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (data.isVip()) {
            return false;
        }
        slot.assign(player.getUUID());
        playerToVip.put(player.getUUID(), slot.vipId());
        vipToPlayer.put(slot.vipId(), player.getUUID());
        data.setVipId(slot.vipId());
        reputationEngine.initializeDefaults(data);
        slot.repStartPositive().forEach((key, value) ->
            reputationEngine.adjustPositive(data, ReputationDimension.parse(key), value));
        slot.repStartNegative().forEach((key, value) ->
            reputationEngine.adjustNegative(data, ReputationDimension.parse(key), value));
        data.recordAction("VIP_CLAIM", simYear, "Claimed " + slot.name());
        teleportToSpawn(player, slot);
        return true;
    }

    public void releaseVip(ServerPlayer player, int simYear) {
        if (player == null) {
            return;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        String vipId = data.getVipId();
        if (vipId != null) {
            VipSlot slot = vipDatabase.get(vipId);
            if (slot != null) {
                slot.release();
            }
            vipToPlayer.remove(vipId);
            playerToVip.remove(player.getUUID());
            data.recordAction("VIP_RELEASE", simYear, "Released " + vipId);
        }
        data.setVipId(null);
    }

    public boolean assignAfterTakeover(ServerPlayer player, String vipId, int simYear, String cause) {
        if (player == null || vipId == null) {
            return false;
        }
        VipSlot slot = vipDatabase.get(vipId);
        if (slot == null || slot.isClaimedByPlayer()) {
            return false;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        slot.assign(player.getUUID());
        playerToVip.put(player.getUUID(), slot.vipId());
        vipToPlayer.put(slot.vipId(), player.getUUID());
        data.setVipId(slot.vipId());
        data.recordAction("VIP_TAKEOVER", simYear, "Took over " + slot.name() + " cause=" + (cause == null ? "unknown" : cause));
        return true;
    }

    public void onVipDeath(VipSlot vip, String cause, int year) {
        if (vip == null) {
            return;
        }
        UUID playerUuid = vip.assignedPlayerUuid();
        vip.markDead(cause, year);
        if (playerUuid != null) {
            playerToVip.remove(playerUuid);
        }
        vipToPlayer.remove(vip.vipId());
    }

    public Optional<VipSlot> getVip(String id) {
        return Optional.ofNullable(vipDatabase.get(id));
    }

    public Optional<VipSlot> getVipForPlayer(UUID uuid) {
        String vipId = playerToVip.get(uuid);
        return vipId == null ? Optional.empty() : getVip(vipId);
    }

    public void tick(int simYear) {
        for (VipSlot slot : vipDatabase.values()) {
            slot.updateAge(simYear);
            if (!slot.dead() && simYear > slot.historicalDeathYear()) {
                slot.markDead(slot.historicalDeathCause(), simYear);
            }
        }
    }

    public List<VipSlot> allVips() {
        return List.copyOf(vipDatabase.values());
    }

    private boolean preconditionsMet(VipSlot slot, WorldStateManager state) {
        if (state == null) {
            return slot.preconditionIds().isEmpty();
        }
        for (String precondition : slot.preconditionIds()) {
            if (!state.hasFlag(precondition)) {
                return false;
            }
        }
        return true;
    }

    private void teleportToSpawn(ServerPlayer player, VipSlot slot) {
        BlockPos pos = TerraCoordinateHelper.fromGeo(slot.spawnLat(), slot.spawnLon(), player.level());
        if (pos != null) {
            player.teleportTo(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d);
        }
    }

    private void registerDefaults() {
        VipSlot ashoka = new VipSlot("ashoka", "Ashoka Maurya", -304, -232, "maurya",
            VipRoleType.POLITICAL, 25.6d, 85.1d, 8);
        ashoka.addCommand("OFFER_PEACE");
        ashoka.addCommand("BUILD_INFRASTRUCTURE");
        ashoka.putPersonality("mercy", 80);
        vipDatabase.put(ashoka.vipId(), ashoka);

        VipSlot caesar = new VipSlot("caesar", "Julius Caesar", -100, -44, "rome",
            VipRoleType.MILITARY_POLITICAL, 41.9d, 12.5d, 9);
        caesar.addCommand("MOVE_ARMY");
        caesar.addCommand("DECLARE_WAR");
        caesar.putPersonality("ambition", 95);
        vipDatabase.put(caesar.vipId(), caesar);
    }

    private void loadVipFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        addFromJson(element.getAsJsonObject());
                    }
                }
            } else if (root.isJsonObject()) {
                addFromJson(root.getAsJsonObject());
            }
        } catch (Exception ignored) {
            // Keep already loaded VIPs.
        }
    }

    private void addFromJson(JsonObject json) {
        String id = json.has("vipId") ? json.get("vipId").getAsString() : json.get("id").getAsString();
        String name = json.has("name") ? json.get("name").getAsString() : id;
        int birth = json.has("birthYear") ? json.get("birthYear").getAsInt() : -3000;
        int death = json.has("historicalDeathYear") ? json.get("historicalDeathYear").getAsInt() : 9999;
        String civ = json.has("civilizationId") ? json.get("civilizationId").getAsString() : "neutral";
        VipRoleType role = json.has("roleType") ? VipRoleType.valueOf(json.get("roleType").getAsString()) : VipRoleType.POLITICAL;
        double lat = json.has("spawnLat") ? json.get("spawnLat").getAsDouble() : 0.0d;
        double lon = json.has("spawnLon") ? json.get("spawnLon").getAsDouble() : 0.0d;
        int authority = json.has("authorityLevel") ? json.get("authorityLevel").getAsInt() : 5;
        vipDatabase.put(id, new VipSlot(id, name, birth, death, civ, role, lat, lon, authority));
    }
}
