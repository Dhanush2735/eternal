package com.eternalengine.incident;

import net.minecraft.world.Difficulty;

/**
 * Immutable input facts for deterministic incident generation.
 */
public final class IncidentContext {

    private final long worldSeed;
    private final String branchId;
    private final int year;
    private final String regionId;
    private final Difficulty difficulty;
    private final boolean hardcore;
    private final double instability;
    private final double warPressure;
    private final double diseasePressure;
    private final double faminePressure;
    private final double prosperity;

    private IncidentContext(Builder builder) {
        this.worldSeed = builder.worldSeed;
        this.branchId = normalize(builder.branchId, "main");
        this.year = builder.year;
        this.regionId = normalize(builder.regionId, "global");
        this.difficulty = builder.difficulty == null ? Difficulty.NORMAL : builder.difficulty;
        this.hardcore = builder.hardcore;
        this.instability = clamp01(builder.instability);
        this.warPressure = clamp01(builder.warPressure);
        this.diseasePressure = clamp01(builder.diseasePressure);
        this.faminePressure = clamp01(builder.faminePressure);
        this.prosperity = clamp01(builder.prosperity);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public long worldSeed() { return worldSeed; }
    public String branchId() { return branchId; }
    public int year() { return year; }
    public String regionId() { return regionId; }
    public Difficulty difficulty() { return difficulty; }
    public boolean hardcore() { return hardcore; }
    public double instability() { return instability; }
    public double warPressure() { return warPressure; }
    public double diseasePressure() { return diseasePressure; }
    public double faminePressure() { return faminePressure; }
    public double prosperity() { return prosperity; }

    public static final class Builder {
        private long worldSeed;
        private String branchId = "main";
        private int year;
        private String regionId = "global";
        private Difficulty difficulty = Difficulty.NORMAL;
        private boolean hardcore;
        private double instability;
        private double warPressure;
        private double diseasePressure;
        private double faminePressure;
        private double prosperity;

        public Builder worldSeed(long worldSeed) { this.worldSeed = worldSeed; return this; }
        public Builder branchId(String branchId) { this.branchId = branchId; return this; }
        public Builder year(int year) { this.year = year; return this; }
        public Builder regionId(String regionId) { this.regionId = regionId; return this; }
        public Builder difficulty(Difficulty difficulty) { this.difficulty = difficulty; return this; }
        public Builder hardcore(boolean hardcore) { this.hardcore = hardcore; return this; }
        public Builder instability(double instability) { this.instability = instability; return this; }
        public Builder warPressure(double warPressure) { this.warPressure = warPressure; return this; }
        public Builder diseasePressure(double diseasePressure) { this.diseasePressure = diseasePressure; return this; }
        public Builder faminePressure(double faminePressure) { this.faminePressure = faminePressure; return this; }
        public Builder prosperity(double prosperity) { this.prosperity = prosperity; return this; }
        public IncidentContext build() { return new IncidentContext(this); }
    }
}
