package com.eternalengine.trade;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentSeed;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative trade route foundation.
 */
public final class TradeRouteEngine {
    private final ConcurrentHashMap<String, TradeRoute> routes = new ConcurrentHashMap<>();

    public void tick(int simYear, WorldStateManager state, EconomyEngine economyEngine) {
        if (economyEngine == null) {
            return;
        }
        for (TradeRoute route : routes.values()) {
            if (!route.active()) {
                continue;
            }
            long value = calculateRouteValue(route.routeId(), state);
            economyEngine.applyIncome(route.originCivId(), value / 2L, "trade:" + route.routeId(), state);
            economyEngine.applyIncome(route.destCivId(), value / 2L, "trade:" + route.routeId(), state);
            syncRoute(route, state);
        }
    }

    public TradeRoute openRoute(String originCivId, String destCivId, List<String> goods,
                                TransportType type, int year, long baseValue) {
        String id = "trade_" + IncidentSeed.stableHex(originCivId, destCivId, goods, type, year).substring(0, 12);
        TradeRoute route = new TradeRoute(id, originCivId + " ↔ " + destCivId,
            originCivId, destCivId, 0.0d, 0.0d, 0.0d, 0.0d, goods, type, baseValue, 10.0d, year);
        routes.put(id, route);
        // Into the save at once, not at the next trade tick: a route opened just before a stop was lost.
        if (WorldStateManager.isInitialized()) {
            syncRoute(route, WorldStateManager.getInstance());
        }
        return route;
    }

    /**
     * World start (step 14): the trade routes this world had open when it was saved. Before, they lived in
     * memory only and a restart closed them all.
     *
     * @return the number of routes restored
     */
    public int restoreFrom(WorldStateManager state) {
        routes.clear();
        if (state == null) {
            return 0;
        }
        int restored = 0;
        for (WorldStateManager.TradeRouteState t : state.getAllTradeRoutes().values()) {
            if (t == null || t.routeId == null || t.routeId.isBlank() || !t.active) {
                continue;
            }
            TransportType type = TransportType.CARAVAN;
            if (t.transport != null) {
                try {
                    type = TransportType.valueOf(t.transport);
                } catch (IllegalArgumentException ignored) {
                    // an unknown transport name keeps the caravan default
                }
            }
            TradeRoute route = new TradeRoute(t.routeId, t.name, t.originCivId, t.destCivId,
                t.originLat, t.originLon, t.destLat, t.destLon,
                t.goods == null ? List.of() : t.goods, type, t.value, t.danger, t.openedYear);
            routes.put(route.routeId(), route);
            restored++;
        }
        return restored;
    }

    public void closeRoute(String routeId, String reason, int year, WorldStateManager state) {
        TradeRoute route = routes.get(routeId);
        if (route != null) {
            route.close(reason, year);
            if (state != null) {
                state.removeTradeRoute(routeId);
            }
        }
    }

    public void disruptRoute(String routeId, String cause, int year) {
        TradeRoute route = routes.get(routeId);
        if (route != null) {
            route.disrupt(cause, year);
        }
    }

    public CaravanEntityGroup materializeCaravan(String routeId, BlockPos startPos,
                                                 List<BlockPos> waypoints, ServerLevel level,
                                                 LogisticsManager logistics) {
        if (logistics == null || level == null || startPos == null) {
            return null;
        }
        TradeRoute route = routes.get(routeId);
        Map<String, Integer> cargo = new HashMap<>();
        if (route != null && !route.goods().isEmpty()) {
            for (String good : route.goods()) {
                cargo.put(good, 32);
            }
        } else {
            cargo.put("GRAIN", 48);
            cargo.put("TIMBER", 32);
        }
        return logistics.deployCaravan(route, startPos, waypoints, level, cargo);
    }

    public MerchantShipConvoy materializeShipConvoy(String routeId, BlockPos startWaterPos,
                                                    List<BlockPos> waterWaypoints, ServerLevel level,
                                                    LogisticsManager logistics) {
        if (logistics == null || level == null || startWaterPos == null) {
            return null;
        }
        TradeRoute route = routes.get(routeId);
        Map<String, Integer> cargo = new HashMap<>();
        if (route != null && !route.goods().isEmpty()) {
            for (String good : route.goods()) {
                cargo.put(good, 24);
            }
        } else {
            cargo.put("WINE", 24);
            cargo.put("SILK", 16);
            cargo.put("SPICES", 12);
        }
        return logistics.deployMerchantShip(route, startWaterPos, waterWaypoints, level, cargo);
    }

    public long calculateRouteValue(String routeId, WorldStateManager state) {
        TradeRoute route = routes.get(routeId);
        if (route == null || !route.active()) {
            return 0L;
        }
        double dangerPenalty = 1.0d - Math.min(0.90d, route.dangerLevel() / 120.0d);
        double transportBonus = switch (route.transportType()) {
            case FOOT -> 0.6d;
            case DONKEY -> 0.75d;
            case CARAVAN -> 1.0d;
            case SHIP -> 1.4d;
            case TRAIN -> 1.8d;
            case TRUCK -> 2.0d;
            case AIR -> 2.5d;
        };
        return Math.max(0L, Math.round(route.economicValuePerYear() * dangerPenalty * transportBonus));
    }

    public List<TradeRoute> getRoutesBetween(String civ1, String civ2) {
        List<TradeRoute> result = new ArrayList<>();
        for (TradeRoute route : routes.values()) {
            boolean direct = route.originCivId().equals(civ1) && route.destCivId().equals(civ2);
            boolean reverse = route.originCivId().equals(civ2) && route.destCivId().equals(civ1);
            if (direct || reverse) {
                result.add(route);
            }
        }
        return List.copyOf(result);
    }

    public List<TradeRoute> getActiveRoutes() {
        return routes.values().stream().filter(TradeRoute::active).toList();
    }

    public Optional<TradeRoute> getRoute(String routeId) {
        return Optional.ofNullable(routes.get(routeId));
    }

    private void syncRoute(TradeRoute route, WorldStateManager state) {
        if (state == null || route == null || !route.active()) {
            return;
        }
        WorldStateManager.TradeRouteState trs = new WorldStateManager.TradeRouteState();
        trs.routeId = route.routeId();
        trs.name = route.name();
        trs.originCivId = route.originCivId();
        trs.destCivId = route.destCivId();
        trs.value = (int) Math.min(Integer.MAX_VALUE, route.economicValuePerYear());
        trs.active = true;
        trs.goods = List.copyOf(route.goods());
        trs.transport = route.transportType().name();
        trs.danger = route.dangerLevel();
        trs.openedYear = route.openedYear();
        trs.originLat = route.originLat();
        trs.originLon = route.originLon();
        trs.destLat = route.destLat();
        trs.destLon = route.destLon();
        state.addTradeRoute(route.routeId(), trs);
    }
}
