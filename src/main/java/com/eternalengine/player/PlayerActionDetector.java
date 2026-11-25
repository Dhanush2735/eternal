package com.eternalengine.player;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.history.EventChainEngine;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.vip.VipBotRecord;
import com.eternalengine.vip.VipEntityHelper;
import com.eternalengine.vip.VipSlot;
import com.eternalengine.vip.VipTakeoverContext;
import com.eternalengine.vip.VipTakeoverMethod;
import com.eternalengine.vip.VipTakeoverResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge event bridge for player actions.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class PlayerActionDetector {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static final PlayerActionRouter ROUTER = new PlayerActionRouter();
    private static final DivergenceDetector DIVERGENCE_DETECTOR = new DivergenceDetector();

    private PlayerActionDetector() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity killed = event.getEntity();
        String vipId = extractVipId(killed);
        if (vipId != null) {
            handleVipEntityDeath(player, killed, vipId);
            route(player, PlayerActionType.KILL_VIP_CANDIDATE, killed.blockPosition(),
                "killed_vip=" + vipId + "; entity=" + killed.getType().getDescriptionId());
            return;
        }
        PlayerActionType type = classifyKill(killed);
        route(player, type, killed.blockPosition(), "killed=" + killed.getType().getDescriptionId());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        route(player, PlayerActionType.INTERACT_BLOCK, event.getPos(), "right_click_block=" + event.getLevel().getBlockState(event.getPos()).getBlock().getDescriptionId());
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        route(player, PlayerActionType.BREAK_BLOCK, event.getPos(), "break_block=" + event.getState().getBlock().getDescriptionId());
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        route(player, PlayerActionType.PLACE_BLOCK, event.getPos(), "place_block=" + event.getPlacedBlock().getBlock().getDescriptionId());
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        route(player, PlayerActionType.PICKUP_ITEM, player.blockPosition(), "pickup=" + event.getItem().getItem().getDescriptionId());
    }

    public static DivergenceDetector divergenceDetector() {
        return DIVERGENCE_DETECTOR;
    }

    private static PlayerActionType classifyKill(Entity killed) {
        if (killed instanceof Villager) {
            return PlayerActionType.KILL_VILLAGER;
        }
        if (killed instanceof Player) {
            return PlayerActionType.KILL_PLAYER;
        }
        if (killed instanceof Monster) {
            return PlayerActionType.KILL_HOSTILE;
        }
        return PlayerActionType.UNKNOWN;
    }

    private static String extractVipId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return VipEntityHelper.getVipId(entity);
    }

    private static void handleVipEntityDeath(ServerPlayer player, Entity killed, String vipId) {
        if (!SimulationEngine.isInitialized()) {
            return;
        }
        var vipOpt = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (vipOpt.isEmpty()) {
            return;
        }
        VipSlot vip = vipOpt.get();
        int year = ROUTER.currentYear();
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        int tier = vip.authorityLevel() >= 8 ? 1 : 2;
        boolean religious = vip.roleType().name().contains("RELIGIOUS");
        boolean beloved = vip.personality().getOrDefault("mercy", 0) >= 70;
        VipBotRecord record = new VipBotRecord(vip.vipId(), vip.name(), tier, true, true, beloved, religious);
        VipTakeoverContext context = new VipTakeoverContext(player.getUUID().toString(), vip.vipId(),
            VipTakeoverMethod.MURDER, true, year);
        VipTakeoverResult result = SimulationEngine.getInstance().getVipTakeoverSystem()
            .attemptTakeover(data, player.getUUID().toString(), record, context);
        if (result.success()) {
            SimulationEngine.getInstance().getVipSystem().assignAfterTakeover(player, vip.vipId(), year,
                result.appliedReputationAction());
        } else {
            SimulationEngine.getInstance().getVipSystem().onVipDeath(vip, "killed by " + player.getGameProfile().getName(), year);
            data.recordAction("VIP_KILL_NO_TAKEOVER", year, "Killed VIP " + vipId + " but takeover failed: " + result.reason());
        }
    }

    private static void route(ServerPlayer player, PlayerActionType type, BlockPos pos, String details) {
        PlayerAction action = new PlayerAction(
            player.getUUID(),
            player.getGameProfile().getName(),
            type,
            ROUTER.currentYear(),
            player.level().dimension().location().toString(),
            pos.getX(), pos.getY(), pos.getZ(),
            details,
            System.currentTimeMillis()
        );
        ROUTER.route(action);
        DIVERGENCE_DETECTOR.recordAction(action);
        observeInWorld(player, action, pos);
    }

    // ==================== in-world intervention ====================
    //
    // Every player action already flowed through route(). This is the one place where an action can
    // be turned into a history fork, so the wiring is here rather than duplicated across handlers.

    /**
     * The detector, wired to the real world.
     *
     * <p>The locator uses {@code nearestLocalEvent}, which is already gated by the branch
     * suppression filter — so if this event is already gone on the active timeline, this returns
     * empty and the player cannot fork the same event twice. It never returns a regional pin
     * (news from a whole region or country, {@link HistoryGazetteer#isRegional}): those cannot be
     * stopped from one spot, and must not hide the local event the player is standing in.
     *
     * <p>The sink uses {@code stopInWorld}, which permits stopping an event that has already begun.
     */
    private static final InWorldInterventionDetector INTERVENTION = new InWorldInterventionDetector(
        (lat, lon, year) -> HistoryGazetteer.get().nearestLocalEvent(lat, lon, year)
            .map(hit -> new InWorldInterventionDetector.LocatedEvent(
                hit.event().id(),
                hit.event().name(),
                hit.event().lat(),
                hit.event().lon(),
                hit.event().start(),
                hit.event().end(),
                hit.event().radiusKm(),
                hit.event().kind())),
        (event, cause, reason) -> {
            if (!SimulationEngine.isInitialized()) {
                return false;
            }
            EventChainEngine chain = SimulationEngine.getInstance().getEventChainEngine();
            if (chain == null) {
                return false;
            }
            // Seed source: the same one the typed commands use (`source.getLevel().getSeed()`),
            // reached through the server so the detector never needs a CommandSourceStack. A null
            // overworld yields 0 rather than throwing: a fork with a dull seed is still a fork.
            long worldSeed = 0L;
            var server = SimulationEngine.getInstance().getServer();
            if (server != null && server.overworld() != null) {
                worldSeed = server.overworld().getSeed();
            }
            final long seed = worldSeed;
            // Fork straight from the pin's own place and years. It is deliberately NOT looked up in
            // the chain database: only 76 of the 15,017 pins are chain events, and 26/11 is not one
            // of them, so requiring a HistoricalEvent would make almost every intervention a no-op.
            return chain.stopPinInWorld(
                    event.id(), event.name(), event.startYear(), event.endYear(),
                    cause.playerUuid().toString(), reason, seed,
                    WorldStateManager.getInstance(), cause.year());
        });

    /** The live detector, for status commands and tests. */
    public static InWorldInterventionDetector interventionDetector() {
        return INTERVENTION;
    }

    /**
     * Resolve the player's real-world position and ask the detector whether this action changed
     * history. Never throws into a Forge event handler: an intervention failing must not break the
     * game, and a silent failure here is visible in the log.
     */
    private static void observeInWorld(ServerPlayer player, PlayerAction action, BlockPos pos) {
        try {
            if (!SimulationEngine.isInitialized()) {
                return;
            }
            TerraBridge.GeoPoint geo = TerraCoordinateHelper.toGeoPoint(pos);
            if (geo == null) {
                return;
            }
            INTERVENTION.observe(action, geo.latitude(), geo.longitude())
                .ifPresent(message -> player.sendSystemMessage(Component.literal(message)));
        } catch (Exception e) {
            // Divergence must never break the game. The log is the trace.
            LOGGER.warn("[PlayerActionDetector] In-world intervention check failed: {}", e.toString());
        }
    }
}
