package com.eternalengine.buildings;

/**
 * Simple rectangular building style used by the foundation planner.
 */
public final class BuildingStyle {
    private final String styleId;
    private final String buildingType;
    private final int width;
    private final int depth;
    private final int floors;
    private final int floorHeight;
    private final MaterialSet materials;

    public BuildingStyle(String styleId, String buildingType, int width, int depth,
                         int floors, int floorHeight, MaterialSet materials) {
        this.styleId = styleId == null || styleId.isBlank() ? "style_unknown" : styleId;
        this.buildingType = buildingType == null || buildingType.isBlank() ? "generic" : buildingType;
        this.width = Math.max(3, Math.min(32, width));
        this.depth = Math.max(3, Math.min(32, depth));
        this.floors = Math.max(1, Math.min(8, floors));
        this.floorHeight = Math.max(3, Math.min(8, floorHeight));
        this.materials = materials == null ? MaterialSet.forEra("ANCIENT") : materials;
    }

    public int height() {
        return floors * floorHeight + 1;
    }

    public String styleId() { return styleId; }
    public String buildingType() { return buildingType; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int floors() { return floors; }
    public int floorHeight() { return floorHeight; }
    public MaterialSet materials() { return materials; }
}
