package com.eternalengine.civilization;

import java.util.List;

/**
 * Estimates an occupancy start year for a named place that has no sourced date.
 * <p>
 * Gravity (inverse-square from dated cities/towns) + diffusion lag + parent
 * kingdom box as fallback. Never uses a modern census year as a fake founding.
 * Result is {@code estimated}, not a record.
 */
public final class SettlementYearEstimator {

    public static final int UNKNOWN = Integer.MIN_VALUE;

    private SettlementYearEstimator() {}

    public static int estimate(
            double lat,
            double lon,
            SettlementRank rank,
            List<HistoryGazetteer.Site> anchors,
            List<HistoryGazetteer.Border> borders,
            double gravityKm) {
        int gravity = gravityYear(lat, lon, rank, anchors, gravityKm);
        if (gravity != UNKNOWN) {
            return gravity;
        }
        int parent = earliestParentStart(lat, lon, borders);
        if (parent != UNKNOWN) {
            return parent;
        }
        return UNKNOWN;
    }

    private static int gravityYear(
            double lat,
            double lon,
            SettlementRank rank,
            List<HistoryGazetteer.Site> anchors,
            double gravityKm) {
        if (anchors == null || anchors.isEmpty() || gravityKm <= 0) {
            return UNKNOWN;
        }
        double weighted = 0.0d;
        double weightSum = 0.0d;
        double nearestKm = Double.POSITIVE_INFINITY;
        int nearestStart = UNKNOWN;
        for (HistoryGazetteer.Site a : anchors) {
            // Latitude first: one subtraction rejects nearly every anchor outside the gravity
            // radius, before the string comparisons and rank parsing below (exact; see
            // HistoryGazetteer.beyondByLatitude).
            if (a.presentOnly() || HistoryGazetteer.beyondByLatitude(lat, a.lat(), gravityKm)
                    || "estimated".equalsIgnoreCase(a.confidence())) {
                continue;
            }
            SettlementRank ar = a.rankOverride() != null
                    ? SettlementRank.parse(a.rankOverride()) : null;
            if (ar == SettlementRank.VILLAGE) {
                continue;
            }
            if (ar == null && a.urbanStart() == null && a.rankOverride() == null) {
                ar = SettlementRank.CITY;
            }
            if (ar == SettlementRank.VILLAGE) {
                continue;
            }
            double km = HistoricalPresence.haversineKm(lat, lon, a.lat(), a.lon());
            if (km > gravityKm) {
                continue;
            }
            double sig = (ar == SettlementRank.CITY) ? 3.0d : 2.0d;
            double d = Math.max(km, 0.5d);
            double w = sig / (d * d);
            weighted += a.occupiedStart() * w;
            weightSum += w;
            if (km < nearestKm) {
                nearestKm = km;
                nearestStart = a.occupiedStart();
            }
        }
        if (weightSum < 1e-9 || nearestStart == UNKNOWN) {
            return UNKNOWN;
        }
        int pulled = (int) Math.round(weighted / weightSum);
        int lag = diffusionLag(rank, nearestKm);
        return pulled + lag;
    }

    /** Villages appear after the city they orbit; farther → later. */
    private static int diffusionLag(SettlementRank rank, double km) {
        int base = (rank == SettlementRank.CITY) ? 0 : (rank == SettlementRank.TOWN) ? 10 : 20;
        return base + (int) Math.round(km * 0.4d);
    }

    private static int earliestParentStart(double lat, double lon, List<HistoryGazetteer.Border> borders) {
        if (borders == null) {
            return UNKNOWN;
        }
        int best = UNKNOWN;
        for (HistoryGazetteer.Border b : borders) {
            if (lat < b.minLat() || lat > b.maxLat() || lon < b.minLon() || lon > b.maxLon()) {
                continue;
            }
            if ("india".equals(b.civId()) || "india_republic".equals(b.id())) {
                continue;
            }
            if (best == UNKNOWN || b.start() < best) {
                best = b.start();
            }
        }
        return best;
    }
}
