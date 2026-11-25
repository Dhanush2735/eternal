package com.eternalengine.hyderabad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Project an OSM sample without loading chunks or editing the world. */
public final class PilotProjectedPreview {
    @FunctionalInterface
    public interface Projection {
        /** Returns world X/Z for lat/lon; null indicates the world transform is unavailable. */
        double[] project(double latitude, double longitude);
    }

    public static final class Feature {
        public final long osmId;
        public final String name;
        public final boolean building, buildingPart, road;
        public final List<Vertex> vertices;
        Feature(PilotOsmSnapshot.Way way, List<Vertex> vertices) {
            this.osmId = way.osmId;
            this.name = way.tags.getOrDefault("name", "");
            this.building = way.tags.containsKey("building") && !"no".equals(way.tags.get("building"));
            this.buildingPart = way.tags.containsKey("building:part") && !"no".equals(way.tags.get("building:part"));
            this.road = way.tags.containsKey("highway");
            this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
        }
    }
    public static final class Vertex {
        public final int x, z;
        Vertex(int x, int z) { this.x = x; this.z = z; }
    }
    public final List<Feature> features;
    public final int minX, maxX, minZ, maxZ, vertices;
    private PilotProjectedPreview(List<Feature> features, int minX, int maxX, int minZ, int maxZ, int vertices) {
        this.features = Collections.unmodifiableList(new ArrayList<>(features));
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ; this.vertices = vertices;
    }
    private PilotProjectedPreview() { throw new AssertionError(); }

    /** Strictly 2026: an OSM survey is not evidence for a different year. Fails atomically. */
    public static PilotProjectedPreview bundled(int currentYear, Projection projection) throws IOException {
        return project(PilotOsmSnapshot.readBundledCharminar2026(currentYear), projection);
    }

    public static PilotProjectedPreview project(List<PilotOsmSnapshot.Way> ways, Projection projection) throws IOException {
        if (ways == null || ways.isEmpty() || projection == null) throw new IOException("Missing geometry or world projection");
        List<Feature> features = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int count = 0;
        for (PilotOsmSnapshot.Way way : ways) {
            if (way == null || way.geometry == null || way.geometry.isEmpty()) throw new IOException("Missing feature geometry");
            List<Vertex> vertices = new ArrayList<>();
            for (PilotOsmSnapshot.Point point : way.geometry) {
                if (point == null) throw new IOException("Missing OSM point");
                final double[] xz;
                try { xz = projection.project(point.latitude, point.longitude); }
                catch (RuntimeException e) { throw new IOException("World projection failed for OSM way " + way.osmId, e); }
                if (xz == null || xz.length < 2 || !Double.isFinite(xz[0]) || !Double.isFinite(xz[1])
                        || xz[0] < Integer.MIN_VALUE || xz[0] >= (double) Integer.MAX_VALUE + 1.0
                        || xz[1] < Integer.MIN_VALUE || xz[1] >= (double) Integer.MAX_VALUE + 1.0)
                    throw new IOException("World projection unavailable or invalid for OSM way " + way.osmId);
                int x = (int) Math.floor(xz[0]), z = (int) Math.floor(xz[1]);
                vertices.add(new Vertex(x, z)); count++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            features.add(new Feature(way, vertices));
        }
        return new PilotProjectedPreview(features, minX, maxX, minZ, maxZ, count);
    }
}
