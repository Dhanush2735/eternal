package com.eternalengine.carto;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.population.SettlementDensity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Ambient 3D Directional Crowd Noise & Environmental FX Manager.
 * Evaluates proximate HumanNpcEntity population density around players and synthesizes
 * 3D directional murmurs, market bustle, marching cadences, and atmospheric water FX.
 */
public final class AmbientCrowdSoundManager {

    private int tickCounter = 0;

    /**
     * Each player's crowd level and whether soldiers are near, worked out once a second (step 17). It was worked out
     * on every tick -- an entity search and a population lookup per player, 20 times a second, to decide whether a
     * sound is due -- which made this the costliest system in /ee performance (0.8 ms per tick in a town).
     */
    private final java.util.Map<java.util.UUID, double[]> perPlayer = new java.util.HashMap<>();

    private static final int RECOUNT_TICKS = 20;

    public void tick(ServerLevel level) {
        if (level == null || level.players().isEmpty()) return;
        if (!com.eternalengine.civilization.HistoryChecks.historyReady()) return; // still loading (step 17)
        tickCounter++;

        for (ServerPlayer player : level.players()) {
            double[] state = perPlayer.get(player.getUUID());
            if (state == null || tickCounter % RECOUNT_TICKS == 0) {
                AABB searchBox = player.getBoundingBox().inflate(24.0D, 8.0D, 24.0D);
                List<HumanNpcEntity> nearbyNpcs = level.getEntitiesOfClass(HumanNpcEntity.class, searchBox, HumanNpcEntity::isAlive);
                // Sound follows the SIMULATED population, not just the rendered sample, and it follows
                // it continuously: the population maps onto the same 0..1 scale that decides how many
                // people are rendered, so a busy place is both more crowded and louder, in proportion.
                // The visible-NPC count is the floor, so a genuine knot of actors is audible even where
                // the resolver has no figure.
                double intensity = SettlementDensity.intensity(simulatedPopulation(player), nearbyNpcs.size());
                boolean soldiers = nearbyNpcs.stream()
                        .anyMatch(n -> "soldier".equals(n.getRole()) || "royal_guard".equals(n.getRole()));
                state = new double[] {intensity, soldiers ? 1.0d : 0.0d};
                perPlayer.put(player.getUUID(), state);
            }
            double intensity = state[0];
            int interval = SettlementDensity.soundIntervalTicks(intensity);
            if (interval <= 0 || tickCounter % interval != 0) {
                continue;
            }

            if (intensity > 0.12d) {
                player.playNotifySound(
                        SettlementDensity.murmurBand(intensity)
                                ? SoundEvents.VILLAGER_AMBIENT   // bustling: the low murmur
                                : SoundEvents.VILLAGER_YES,      // sparse: an occasional voice
                        SoundSource.AMBIENT,
                        SettlementDensity.soundVolume(intensity),
                        1.0F);
            }

            // Military presence (soldiers or guards)
            if (state[1] > 0.0d) {
                player.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.AMBIENT, 0.35F, 0.9F);
            }
        }
        if (tickCounter % 1200 == 0) {
            java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
            for (ServerPlayer player : level.players()) {
                online.add(player.getUUID());
            }
            perPlayer.keySet().retainAll(online);
        }
    }

    /**
     * Population of the place this player is standing in, or 0 when nothing occupies it.
     * Cheap and cached upstream by the resolver; this is called at most once every four seconds.
     */
    private static long simulatedPopulation(net.minecraft.server.level.ServerPlayer player) {
        try {
            var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(player.blockPosition());
            if (geo == null) {
                return 0L;
            }
            int year = com.eternalengine.clock.SimulationClockManager.isInitialized()
                    ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                    : 0;
            var presence = com.eternalengine.core.WorldQuery.presence(
                    com.eternalengine.core.SimulationEngine.getInstance().getCivilizationManager(),
                    com.eternalengine.core.WorldKey.at(player.level(), player.blockPosition(), year));
            if (!presence.occupied()) {
                return 0L;
            }
            return com.eternalengine.population.PopulationResolver.get().resolve(
                    presence.site().map(h -> h.site().id()).orElse(null),
                    geo.latitude(), geo.longitude(), year, presence.rank()).population();
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
