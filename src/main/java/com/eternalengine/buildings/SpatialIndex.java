package com.eternalengine.buildings;

import com.eternalengine.core.AsyncTaskManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe simple spatial index foundation.
 *
 * This is implemented as a bounded map scan now. It preserves the R-Tree/QuadTree
 * API shape so internals can be replaced later without changing callers.
 */
public final class SpatialIndex {
    private final ConcurrentHashMap<String, IndexEntry> entries = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean corrupt;

    public void insert(String id, double lat, double lon, Object data) {
        if (id == null || id.isBlank() || Double.isNaN(lat) || Double.isNaN(lon)) {
            return;
        }
        lock.writeLock().lock();
        try {
            entries.put(id, new IndexEntry(id, lat, lon, data));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<IndexEntry> queryRadius(double lat, double lon, double radiusMeters) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || radiusMeters < 0.0d) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<IndexEntry> result = new ArrayList<>();
            for (IndexEntry entry : entries.values()) {
                double distance = haversineMeters(lat, lon, entry.lat(), entry.lon());
                if (distance <= radiusMeters) {
                    result.add(entry);
                }
            }
            result.sort(Comparator.comparingDouble(e -> haversineMeters(lat, lon, e.lat(), e.lon())));
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<IndexEntry> queryBounds(double minLat, double maxLat, double minLon, double maxLon) {
        double loLat = Math.min(minLat, maxLat);
        double hiLat = Math.max(minLat, maxLat);
        double loLon = Math.min(minLon, maxLon);
        double hiLon = Math.max(minLon, maxLon);
        lock.readLock().lock();
        try {
            List<IndexEntry> result = new ArrayList<>();
            for (IndexEntry entry : entries.values()) {
                if (entry.lat() >= loLat && entry.lat() <= hiLat && entry.lon() >= loLon && entry.lon() <= hiLon) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void remove(String id) {
        if (id == null) return;
        lock.writeLock().lock();
        try {
            entries.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            entries.clear();
            corrupt = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void persist(Path file) {
        if (file == null) return;
        AsyncTaskManager.getInstance().submit("spatial_index_persist_" + file.getFileName(), () -> {
            try {
                Files.createDirectories(file.getParent());
                JsonObject root = new JsonObject();
                JsonArray array = new JsonArray();
                lock.readLock().lock();
                try {
                    for (IndexEntry entry : entries.values()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id", entry.id());
                        obj.addProperty("lat", entry.lat());
                        obj.addProperty("lon", entry.lon());
                        array.add(obj);
                    }
                } finally {
                    lock.readLock().unlock();
                }
                root.add("entries", array);
                Files.writeString(file, new Gson().toJson(root));
            } catch (Exception e) {
                corrupt = true;
            }
        });
    }

    public void load(Path file) {
        if (file == null || !Files.exists(file)) return;
        lock.writeLock().lock();
        try {
            entries.clear();
            JsonObject root = new Gson().fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("entries")) {
                corrupt = true;
                return;
            }
            for (var element : root.getAsJsonArray("entries")) {
                JsonObject obj = element.getAsJsonObject();
                insert(obj.get("id").getAsString(), obj.get("lat").getAsDouble(), obj.get("lon").getAsDouble(), null);
            }
            corrupt = false;
        } catch (Exception e) {
            corrupt = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        return r * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
    }

    public record IndexEntry(String id, double lat, double lon, Object data) {
    }
}
