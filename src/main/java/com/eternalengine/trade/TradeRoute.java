package com.eternalengine.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable trade route state.
 */
public final class TradeRoute {
    private final String routeId;
    private final String name;
    private final String originCivId;
    private final String destCivId;
    private final double originLat;
    private final double originLon;
    private final double destLat;
    private final double destLon;
    private final List<String> goods;
    private final TransportType transportType;
    private long economicValuePerYear;
    private double dangerLevel;
    private boolean active;
    private final int openedYear;
    private int closedYear;
    private final List<String> disruptionHistory;

    public TradeRoute(String routeId, String name, String originCivId, String destCivId,
                      double originLat, double originLon, double destLat, double destLon,
                      List<String> goods, TransportType transportType, long economicValuePerYear,
                      double dangerLevel, int openedYear) {
        this.routeId = routeId == null || routeId.isBlank() ? "trade_route_unknown" : routeId;
        this.name = name == null || name.isBlank() ? this.routeId : name;
        this.originCivId = originCivId == null || originCivId.isBlank() ? "neutral" : originCivId;
        this.destCivId = destCivId == null || destCivId.isBlank() ? "neutral" : destCivId;
        this.originLat = originLat;
        this.originLon = originLon;
        this.destLat = destLat;
        this.destLon = destLon;
        this.goods = new ArrayList<>(goods == null ? List.of() : goods);
        this.transportType = transportType == null ? TransportType.CARAVAN : transportType;
        this.economicValuePerYear = Math.max(0L, economicValuePerYear);
        this.dangerLevel = clamp100(dangerLevel);
        this.active = true;
        this.openedYear = openedYear;
        this.closedYear = -1;
        this.disruptionHistory = new ArrayList<>();
    }

    public void close(String reason, int year) {
        active = false;
        closedYear = year;
        addDisruption("closed: " + reason + " at " + year);
    }

    public void disrupt(String cause, int year) {
        dangerLevel = clamp100(dangerLevel + 10.0d);
        addDisruption(cause + " at " + year);
    }

    public void addDisruption(String entry) {
        if (entry != null && !entry.isBlank()) {
            disruptionHistory.add(entry);
        }
    }

    private double clamp100(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    public String routeId() { return routeId; }
    public String name() { return name; }
    public String originCivId() { return originCivId; }
    public String destCivId() { return destCivId; }
    public double originLat() { return originLat; }
    public double originLon() { return originLon; }
    public double destLat() { return destLat; }
    public double destLon() { return destLon; }
    public List<String> goods() { return Collections.unmodifiableList(goods); }
    public TransportType transportType() { return transportType; }
    public long economicValuePerYear() { return economicValuePerYear; }
    public void setEconomicValuePerYear(long value) { this.economicValuePerYear = Math.max(0L, value); }
    public double dangerLevel() { return dangerLevel; }
    public boolean active() { return active; }
    public int openedYear() { return openedYear; }
    public int closedYear() { return closedYear; }
    public List<String> disruptionHistory() { return Collections.unmodifiableList(disruptionHistory); }
}
