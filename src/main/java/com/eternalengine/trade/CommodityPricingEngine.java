package com.eternalengine.trade;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic commodity pricing engine simulating supply, demand,
 * transport distance, and route hazard premiums across civilizations.
 */
public final class CommodityPricingEngine {

    /**
     * Baseline commodities with base unit prices in standard currency (silver / emerald value).
     */
    public record CommoditySpec(String name, double basePrice, double weightKg, String category) {}

    private final ConcurrentHashMap<String, CommoditySpec> commodityRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> regionalSupply = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> regionalDemand = new ConcurrentHashMap<>();

    public CommodityPricingEngine() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Staples
        register(new CommoditySpec("WHEAT", 2.0D, 1.0D, "STAPLE"));
        register(new CommoditySpec("BREAD", 3.0D, 0.5D, "STAPLE"));
        register(new CommoditySpec("TIMBER", 4.0D, 2.0D, "MATERIAL"));
        register(new CommoditySpec("STONE", 2.5D, 3.0D, "MATERIAL"));
        // Strategic metals & fuel
        register(new CommoditySpec("COAL", 5.0D, 1.5D, "FUEL"));
        register(new CommoditySpec("IRON_INGOT", 10.0D, 2.0D, "STRATEGIC"));
        register(new CommoditySpec("COPPER_INGOT", 7.0D, 1.8D, "STRATEGIC"));
        // Luxuries & manufactured goods
        register(new CommoditySpec("WOOL", 6.0D, 0.8D, "LUXURY"));
        register(new CommoditySpec("WINE", 14.0D, 1.2D, "LUXURY"));
        register(new CommoditySpec("SILK", 28.0D, 0.4D, "LUXURY"));
        register(new CommoditySpec("SPICES", 35.0D, 0.3D, "LUXURY"));
        register(new CommoditySpec("GOLD_INGOT", 55.0D, 2.5D, "PRECIOUS"));
        register(new CommoditySpec("EMERALD", 65.0D, 0.2D, "PRECIOUS"));
    }

    public void register(CommoditySpec spec) {
        if (spec != null) {
            commodityRegistry.put(spec.name().toUpperCase(Locale.ROOT), spec);
        }
    }

    public CommoditySpec getSpec(String commodity) {
        if (commodity == null) return null;
        return commodityRegistry.get(commodity.toUpperCase(Locale.ROOT));
    }

    /**
     * Calculates the unit price of a commodity for delivery between an origin and destination civilization.
     * Incorporates distance transport costs, route danger markup, and local supply/demand ratios.
     */
    public double calculateUnitPrice(String commodity, String originCiv, String destCiv,
                                     double distanceKm, double dangerLevel) {
        CommoditySpec spec = getSpec(commodity);
        if (spec == null) {
            return 10.0D; // Default fallback price
        }

        double base = spec.basePrice();

        // Distance freight cost: +3% per 100km
        double distanceMultiplier = 1.0D + (Math.max(0.0D, distanceKm) * 0.0003D);

        // Danger hazard markup: up to +80% for perilous routes
        double dangerMultiplier = 1.0D + (Math.max(0.0D, Math.min(100.0D, dangerLevel)) * 0.008D);

        // Local supply & demand ratio at destination
        int supply = getRegionalStock(destCiv, spec.name());
        int demand = getRegionalDemand(destCiv, spec.name());
        double marketRatio = (double) Math.max(1, demand) / (double) Math.max(1, supply);
        double supplyDemandMultiplier = Math.max(0.4D, Math.min(3.0D, marketRatio));

        double finalPrice = base * distanceMultiplier * dangerMultiplier * supplyDemandMultiplier;
        return Math.round(finalPrice * 100.0D) / 100.0D;
    }

    public int getRegionalStock(String civId, String commodity) {
        if (civId == null || commodity == null) return 50;
        return regionalSupply.computeIfAbsent(civId.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>())
            .getOrDefault(commodity.toUpperCase(Locale.ROOT), 50);
    }

    public int getRegionalDemand(String civId, String commodity) {
        if (civId == null || commodity == null) return 50;
        return regionalDemand.computeIfAbsent(civId.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>())
            .getOrDefault(commodity.toUpperCase(Locale.ROOT), 50);
    }

    public void adjustSupply(String civId, String commodity, int delta) {
        if (civId == null || commodity == null) return;
        var map = regionalSupply.computeIfAbsent(civId.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>());
        String key = commodity.toUpperCase(Locale.ROOT);
        int current = map.getOrDefault(key, 50);
        map.put(key, Math.max(5, current + delta));
    }

    public void adjustDemand(String civId, String commodity, int delta) {
        if (civId == null || commodity == null) return;
        var map = regionalDemand.computeIfAbsent(civId.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>());
        String key = commodity.toUpperCase(Locale.ROOT);
        int current = map.getOrDefault(key, 50);
        map.put(key, Math.max(5, current + delta));
    }

    public Map<String, CommoditySpec> getAllCommodities() {
        return Collections.unmodifiableMap(commodityRegistry);
    }
}
