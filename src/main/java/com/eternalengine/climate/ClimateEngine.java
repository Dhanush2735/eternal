package com.eternalengine.climate;

import com.eternalengine.agriculture.AgricultureEngine;
import com.eternalengine.civilization.Civilization;
import com.eternalengine.civilization.CivilizationManager;
import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Climate simulation foundation.
 *
 * <p>Step 35 (B4): the era's climate now comes from {@link ClimatePeriods} — eighty-three dated
 * periods loaded from {@code history/climate_periods.json.gz} — instead of the two events that
 * used to be typed in below. The change that matters is that <b>climate is now a question with a
 * place in it</b>: the same year is a warm one in the North Atlantic and a megadrought on the
 * Yucatan, and the two are not the same episode. See that class for why.
 *
 * <p>{@link LittleIceAgeSystem} is kept — other code asks it — but it is only consulted when the
 * period table could not be read, because the table carries the Little Ice Age itself and counting
 * both would be double.
 */
public final class ClimateEngine {
    private final List<ClimateEvent> events = new ArrayList<>();
    private final ConcurrentHashMap<String, Double> regionTempModifier = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> regionPrecipModifier = new ConcurrentHashMap<>();
    private final LittleIceAgeSystem littleIceAgeSystem = new LittleIceAgeSystem();

    public ClimateEngine() {
        registerDefaults();
    }

    public void registerEvent(ClimateEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public void tick(int simYear, WorldStateManager state, AgricultureEngine agricultureEngine) {
        tick(simYear, state, agricultureEngine, null);
    }

    /**
     * With a civilization registry, each region is asked about at the coordinates of its capital,
     * so a civ gets the weather of the country it is actually in. Without one, only the periods
     * that reached everywhere can be counted — see {@link ClimatePeriods#activeEverywhere(int)}.
     */
    public void tick(int simYear, WorldStateManager state, AgricultureEngine agricultureEngine,
                     CivilizationManager civilizations) {
        if (state == null) {
            return;
        }
        for (String regionId : state.getAllCivilizationIds()) {
            double lat = Double.NaN;
            double lon = Double.NaN;
            Civilization civ = civilizations == null ? null
                    : civilizations.getCivilization(regionId).orElse(null);
            if (civ != null && !(civ.capitalLat() == 0.0d && civ.capitalLon() == 0.0d)) {
                lat = civ.capitalLat();
                lon = civ.capitalLon();
            }
            double temp = getTemperatureModifier(regionId, simYear, lat, lon);
            double precip = getPrecipModifier(regionId, simYear, lat, lon);
            state.setFlag("climate.temp." + regionId, Double.toString(temp));
            state.setFlag("climate.precip." + regionId, Double.toString(precip));
            state.setFlag("climate.periods." + regionId, periodIds(simYear, lat, lon));
            if (agricultureEngine != null) {
                double yieldModifier = yieldModifier(regionId, simYear, lat, lon);
                agricultureEngine.applyClimateEffect(regionId, yieldModifier);
                state.setFlag("climate.yield." + regionId, Double.toString(yieldModifier));
            }
        }
        state.setFlag("climate.little_ice_age", Boolean.toString(isLittleIceAgeActive(simYear)));
    }

    /** Without a location, only the periods that reached everywhere can be counted. */
    public double getTemperatureModifier(String regionId, int year) {
        return getTemperatureModifier(regionId, year, Double.NaN, Double.NaN);
    }

    public double getTemperatureModifier(String regionId, int year, double lat, double lon) {
        double value = regionTempModifier.getOrDefault(regionId, 0.0d);
        value += periodTemperature(year, lat, lon);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            value += event.tempChangeDegrees();
        }
        return value;
    }

    /** Without a location, only the periods that reached everywhere can be counted. */
    public double getPrecipModifier(String regionId, int year) {
        return getPrecipModifier(regionId, year, Double.NaN, Double.NaN);
    }

    public double getPrecipModifier(String regionId, int year, double lat, double lon) {
        double value = regionPrecipModifier.getOrDefault(regionId, 0.0d);
        value += periodPrecip(year, lat, lon);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            value += event.precipChangePercent();
        }
        return value;
    }

    /** 1.0 is a normal harvest; 0.5 is half a crop. Clamped to [0.05, 3.0]. */
    public double yieldModifier(String regionId, int year) {
        return yieldModifier(regionId, year, Double.NaN, Double.NaN);
    }

    public double yieldModifier(String regionId, int year, double lat, double lon) {
        double modifier = periodYield(year, lat, lon);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            modifier *= Math.max(0.0d, 1.0d + event.yieldImpactPercent() / 100.0d);
        }
        return Math.max(ClimatePeriods.YIELD_MIN, Math.min(ClimatePeriods.YIELD_MAX, modifier));
    }

    public boolean isLittleIceAgeActive(int year) {
        ClimatePeriods table = ClimatePeriods.get();
        if (table.loaded()) {
            for (ClimatePeriods.Period p : table.activeEverywhere(year)) {
                if ("little_ice_age".equals(p.id())) {
                    return true;
                }
            }
            // The Little Ice Age is a North Atlantic and European episode; if the caller has no
            // place, ask at its centre rather than pretending it was a world-wide one.
            return table.isActive("little_ice_age", 55.0d, -10.0d, year);
        }
        return littleIceAgeSystem.isActive(year);
    }

    public List<ClimateEvent> getActiveClimateEvents(int year) {
        List<ClimateEvent> active = new ArrayList<>();
        for (ClimateEvent event : events) {
            if (event.isActiveInYear(year)) {
                active.add(event);
            }
        }
        return List.copyOf(active);
    }

    /** The loaded era table, whether or not it could be read. */
    public ClimatePeriods periods() {
        return ClimatePeriods.get();
    }

    public void setRegionalTempModifier(String regionId, double modifier) {
        if (regionId != null) {
            regionTempModifier.put(regionId, modifier);
        }
    }

    public void setRegionalPrecipModifier(String regionId, double modifier) {
        if (regionId != null) {
            regionPrecipModifier.put(regionId, modifier);
        }
    }

    public List<ClimateEvent> events() {
        return List.copyOf(events);
    }

    private double periodTemperature(int year, double lat, double lon) {
        ClimatePeriods table = ClimatePeriods.get();
        if (table.loaded()) {
            return Double.isNaN(lat) ? table.temperatureModifierEverywhere(year)
                    : table.temperatureModifier(lat, lon, year);
        }
        return littleIceAgeSystem.temperatureModifier(year);
    }

    private double periodPrecip(int year, double lat, double lon) {
        ClimatePeriods table = ClimatePeriods.get();
        if (table.loaded()) {
            return Double.isNaN(lat) ? table.precipModifierEverywhere(year)
                    : table.precipModifier(lat, lon, year);
        }
        return 0.0d;
    }

    private double periodYield(int year, double lat, double lon) {
        ClimatePeriods table = ClimatePeriods.get();
        if (table.loaded()) {
            return Double.isNaN(lat) ? table.yieldModifierEverywhere(year)
                    : table.yieldModifier(lat, lon, year);
        }
        return littleIceAgeSystem.yieldModifier(year);
    }

    private String periodIds(int year, double lat, double lon) {
        ClimatePeriods table = ClimatePeriods.get();
        if (!table.loaded()) {
            return "";
        }
        return Double.isNaN(lat) ? "" : table.activeIds(lat, lon, year);
    }

    /**
     * The era table is the register's defaults now. The two events below are typed in only when it
     * could not be read, so that a missing resource costs history rather than costing the whole
     * climate — and so that they are never counted twice.
     */
    private void registerDefaults() {
        if (ClimatePeriods.get().loaded()) {
            return;
        }
        registerEvent(new ClimateEvent("little_ice_age", "Little Ice Age", "COOLING",
            1300, 1850, 6.0d, 0.0d, 50.0d, 0.0d, -0.6d, 0.0d, -8.0d));
        registerEvent(new ClimateEvent("year_without_summer", "Year Without a Summer", "VOLCANIC",
            1816, 1816, 8.0d, 0.0d, 0.0d, 0.0d, -2.0d, 10.0d, -25.0d));
    }
}
