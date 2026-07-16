package com.eternalengine.history;

import com.eternalengine.EternalEngineMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Historical event definition database and indexes.
 */
public final class HistoricalEventDatabase {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private final Map<String, HistoricalEvent> byId = new HashMap<>();
    private final TreeMap<Integer, List<HistoricalEvent>> byYear = new TreeMap<>();
    private final Map<String, List<HistoricalEvent>> byRegion = new HashMap<>();
    private final Map<String, List<HistoricalEvent>> byType = new HashMap<>();

    public void loadFromFiles(Path dataDir) {
        clear();
        if (dataDir == null || !Files.exists(dataDir)) {
            LOGGER.warn("[HistoricalEventDatabase] Data directory missing: {}", dataDir);
            return;
        }
        try (var stream = Files.walk(dataDir)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                .forEach(this::loadFileSafely);
        } catch (Exception e) {
            LOGGER.error("[HistoricalEventDatabase] Failed to scan {}: {}", dataDir, e.getMessage());
        }
        validateChains();
        LOGGER.info("[HistoricalEventDatabase] Loaded {} events", byId.size());
    }

    public void addEvent(HistoricalEvent event) {
        if (event == null) {
            return;
        }
        byId.put(event.id(), event);
        byYear.computeIfAbsent(event.year(), y -> new ArrayList<>()).add(event);
        byRegion.computeIfAbsent(event.regionId(), r -> new ArrayList<>()).add(event);
        byType.computeIfAbsent(event.type(), t -> new ArrayList<>()).add(event);
    }

    public Optional<HistoricalEvent> getEvent(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<HistoricalEvent> getEventsInYear(int year) {
        return List.copyOf(byYear.getOrDefault(year, List.of()));
    }

    public List<HistoricalEvent> getEventsInYearRange(int start, int end) {
        List<HistoricalEvent> result = new ArrayList<>();
        byYear.subMap(Math.min(start, end), true, Math.max(start, end), true)
            .values().forEach(result::addAll);
        return List.copyOf(result);
    }

    public List<HistoricalEvent> getEventsInRegion(String regionId) {
        return List.copyOf(byRegion.getOrDefault(regionId, List.of()));
    }

    public List<HistoricalEvent> getEventsOfType(String type) {
        return List.copyOf(byType.getOrDefault(type, List.of()));
    }

    public List<HistoricalEvent> getChainedEvents(String eventId) {
        HistoricalEvent event = byId.get(eventId);
        if (event == null) {
            return List.of();
        }
        List<HistoricalEvent> result = new ArrayList<>();
        for (String nextId : event.nextChainEventIds()) {
            HistoricalEvent next = byId.get(nextId);
            if (next != null) {
                result.add(next);
            }
        }
        return List.copyOf(result);
    }

    public List<HistoricalEvent> getPendingEvents(int currentYear, com.eternalengine.core.WorldStateManager state,
                                                  java.util.Set<String> triggeredEventIds,
                                                  java.util.Set<String> preventedEventIds) {
        List<HistoricalEvent> result = new ArrayList<>();
        for (HistoricalEvent event : getEventsInYearRange(Integer.MIN_VALUE / 2, currentYear)) {
            if ((triggeredEventIds != null && triggeredEventIds.contains(event.id()))
                || (preventedEventIds != null && preventedEventIds.contains(event.id()))) {
                continue;
            }
            if (event.isTriggered(state, triggeredEventIds)) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    public Collection<HistoricalEvent> getAllEvents() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int getSize() {
        return byId.size();
    }

    private void loadFileSafely(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        addEvent(HistoricalEvent.fromJson(element.getAsJsonObject()));
                    }
                }
            } else if (root.isJsonObject()) {
                addEvent(HistoricalEvent.fromJson(root.getAsJsonObject()));
            }
        } catch (Exception e) {
            LOGGER.error("[HistoricalEventDatabase] Failed to load {}: {}", path, e.getMessage());
        }
    }

    private void validateChains() {
        for (HistoricalEvent event : byId.values()) {
            for (String next : event.nextChainEventIds()) {
                if (!byId.containsKey(next)) {
                    LOGGER.error("[HistoricalEventDatabase] Broken chain: {} -> {}", event.id(), next);
                }
            }
        }
    }

    private void clear() {
        byId.clear();
        byYear.clear();
        byRegion.clear();
        byType.clear();
    }
}
