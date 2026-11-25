package com.eternalengine.carto;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.disease.DiseaseEngine;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.MapOverlayPacket;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.trade.CaravanEntityGroup;
import com.eternalengine.trade.LogisticsManager;
import com.eternalengine.trade.MerchantShipConvoy;
import com.eternalengine.war.RegimentCohort;
import com.eternalengine.war.WarEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartographic Minimap & World Map Overlay Manager.
 * Formulates and broadcasts geopolitical territorial borders, active war frontlines,
 * moving trade caravans, and epidemic disease hotspots for JourneyMap and Xaero's minimap.
 */
public final class CartographicOverlayManager {

    private int broadcastTickCounter = 0;

    public void tick(ServerLevel level, WorldStateManager state, WarEngine warEngine,
                     LogisticsManager logistics, DiseaseEngine diseaseEngine) {
        if (level == null || level.players().isEmpty()) return;
        broadcastTickCounter++;

        // Broadcast map overlay layers every 100 ticks (5 seconds)
        if (broadcastTickCounter % 100 == 0) {
            for (ServerPlayer player : level.players()) {
                syncOverlaysToPlayer(player, state, warEngine, logistics, diseaseEngine);
            }
        }
    }

    public void syncOverlaysToPlayer(ServerPlayer player, WorldStateManager state, WarEngine warEngine,
                                     LogisticsManager logistics, DiseaseEngine diseaseEngine) {
        if (player == null) return;

        // 1. Trade Caravan & Maritime Fleet Layer
        if (logistics != null) {
            List<MapOverlayPacket.OverlayPoint> tradePoints = new ArrayList<>();
            for (CaravanEntityGroup caravan : logistics.getActiveCaravans()) {
                if (caravan.isAlive()) {
                    BlockPos p = caravan.waypoints().isEmpty() ? player.blockPosition() : caravan.waypoints().get(caravan.currentWaypointIndex());
                    MapOverlayPacket.OverlayPoint pt = new MapOverlayPacket.OverlayPoint();
                    pt.latitude = p.getX();
                    pt.longitude = p.getZ();
                    pt.color = 0xFFFFD700; // Gold
                    pt.intensity = 1.0F;
                    pt.label = "Caravan: " + caravan.routeId();
                    pt.ownerId = caravan.originCivId();
                    tradePoints.add(pt);
                }
            }
            for (MerchantShipConvoy ship : logistics.getActiveShips()) {
                if (ship.isAlive()) {
                    MapOverlayPacket.OverlayPoint pt = new MapOverlayPacket.OverlayPoint();
                    pt.latitude = player.getX();
                    pt.longitude = player.getZ();
                    pt.color = 0xFF00FFFF; // Cyan
                    pt.intensity = 1.0F;
                    pt.label = "Ship: " + ship.convoyId();
                    pt.ownerId = ship.originPort();
                    tradePoints.add(pt);
                }
            }
            if (!tradePoints.isEmpty()) {
                MapOverlayPacket packet = new MapOverlayPacket("TRADE_ROUTE", -180, -90, 180, 90, tradePoints);
                EternalEnginePacketHandler.sendToPlayer(packet, player);
            }
        }

        // 2. War Frontline & Army Regiment Layer
        if (warEngine != null) {
            List<MapOverlayPacket.OverlayPoint> warPoints = new ArrayList<>();
            for (RegimentCohort cohort : warEngine.armyManager().getActiveCohorts()) {
                if (cohort.isAlive()) {
                    BlockPos p = cohort.anchorPos();
                    MapOverlayPacket.OverlayPoint pt = new MapOverlayPacket.OverlayPoint();
                    pt.latitude = p.getX();
                    pt.longitude = p.getZ();
                    pt.color = 0xFFFF0000; // Red
                    pt.intensity = 1.0F;
                    pt.label = "Cohort " + cohort.cohortId() + " (" + cohort.formation() + ")";
                    pt.ownerId = cohort.civilizationId();
                    warPoints.add(pt);
                }
            }
            if (!warPoints.isEmpty()) {
                MapOverlayPacket packet = new MapOverlayPacket("WAR_FRONT", -180, -90, 180, 90, warPoints);
                EternalEnginePacketHandler.sendToPlayer(packet, player);
            }
        }

        // 3. Epidemic Hotspots & Quarantines Layer
        if (diseaseEngine != null) {
            List<MapOverlayPacket.OverlayPoint> diseasePoints = new ArrayList<>();
            for (var checkpoint : diseaseEngine.checkpointController().getActiveCheckpoints()) {
                if (checkpoint.isActive()) {
                    BlockPos p = checkpoint.gatePos();
                    MapOverlayPacket.OverlayPoint pt = new MapOverlayPacket.OverlayPoint();
                    pt.latitude = p.getX();
                    pt.longitude = p.getZ();
                    pt.color = 0xFF8A2BE2; // Biohazard purple
                    pt.intensity = 0.8F;
                    pt.label = "Quarantine: " + checkpoint.regionId();
                    pt.ownerId = checkpoint.regionId();
                    diseasePoints.add(pt);
                }
            }
            if (!diseasePoints.isEmpty()) {
                MapOverlayPacket packet = new MapOverlayPacket("DISEASE", -180, -90, 180, 90, diseasePoints);
                EternalEnginePacketHandler.sendToPlayer(packet, player);
            }
        }
    }
}
