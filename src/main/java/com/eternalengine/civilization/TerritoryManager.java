package com.eternalengine.civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory ownership and point-in-polygon foundation.
 */
public final class TerritoryManager {
    private final ConcurrentHashMap<String, TerritoryPolygon> territories = new ConcurrentHashMap<>();

    public Optional<String> getOwner(double lat, double lon, int year) {
        for (TerritoryPolygon polygon : territories.values()) {
            if (polygon.contains(lat, lon)) {
                return Optional.ofNullable(polygon.ownerCivId());
            }
        }
        return Optional.empty();
    }

    public void addTerritory(TerritoryPolygon territory) {
        if (territory != null) {
            territories.put(territory.territoryId(), territory);
        }
    }

    public void transferTerritory(String territoryId, String fromCivId, String toCivId) {
        TerritoryPolygon polygon = territories.get(territoryId);
        if (polygon != null && (fromCivId == null || fromCivId.equals(polygon.ownerCivId()))) {
            polygon.setOwnerCivId(toCivId);
        }
    }

    public List<TerritoryPolygon> getTerritoriesOf(String civId) {
        List<TerritoryPolygon> result = new ArrayList<>();
        for (TerritoryPolygon territory : territories.values()) {
            if (territory.ownerCivId().equals(civId)) {
                result.add(territory);
            }
        }
        return List.copyOf(result);
    }

    public boolean isContested(String territoryId) {
        TerritoryPolygon polygon = territories.get(territoryId);
        return polygon != null && polygon.contested();
    }

    public double calculateBorderLength(String civId) {
        double total = 0.0d;
        for (TerritoryPolygon territory : getTerritoriesOf(civId)) {
            total += territory.approxPerimeterDegrees();
        }
        return total;
    }

    public List<TerritoryPolygon> getContestedTerritories() {
        return territories.values().stream().filter(TerritoryPolygon::contested).toList();
    }

    public int size() {
        return territories.size();
    }

    public static final class TerritoryPolygon {
        private final String territoryId;
        private String ownerCivId;
        private final List<double[]> boundary;
        private final double centerLat;
        private final double centerLon;
        private boolean contested;
        private int contestedSince;

        public TerritoryPolygon(String territoryId, String ownerCivId, List<double[]> boundary,
                                double centerLat, double centerLon) {
            this.territoryId = territoryId == null || territoryId.isBlank() ? "territory_unknown" : territoryId;
            this.ownerCivId = ownerCivId == null || ownerCivId.isBlank() ? "neutral" : ownerCivId;
            this.boundary = new ArrayList<>(boundary == null ? List.of() : boundary);
            this.centerLat = centerLat;
            this.centerLon = centerLon;
        }

        public boolean contains(double lat, double lon) {
            if (boundary.size() < 3) {
                return false;
            }
            boolean inside = false;
            for (int i = 0, j = boundary.size() - 1; i < boundary.size(); j = i++) {
                double[] pi = boundary.get(i);
                double[] pj = boundary.get(j);
                double yi = pi[0], xi = pi[1];
                double yj = pj[0], xj = pj[1];
                boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / ((yj - yi) == 0.0d ? 1e-9d : (yj - yi)) + xi);
                if (intersect) inside = !inside;
            }
            return inside;
        }

        public double approxPerimeterDegrees() {
            if (boundary.size() < 2) return 0.0d;
            double total = 0.0d;
            for (int i = 0; i < boundary.size(); i++) {
                double[] a = boundary.get(i);
                double[] b = boundary.get((i + 1) % boundary.size());
                double dLat = a[0] - b[0];
                double dLon = a[1] - b[1];
                total += Math.sqrt(dLat * dLat + dLon * dLon);
            }
            return total;
        }

        public String territoryId() { return territoryId; }
        public String ownerCivId() { return ownerCivId; }
        public void setOwnerCivId(String ownerCivId) { this.ownerCivId = ownerCivId == null ? "neutral" : ownerCivId; }
        public List<double[]> boundary() { return List.copyOf(boundary); }
        public double centerLat() { return centerLat; }
        public double centerLon() { return centerLon; }
        public boolean contested() { return contested; }
        public int contestedSince() { return contestedSince; }
        public void setContested(boolean contested, int year) { this.contested = contested; this.contestedSince = contested ? year : 0; }
    }
}
