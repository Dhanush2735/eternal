package com.eternalengine.war;

/**
 * Tactical military formations for physicalized regiments in Eternal Engine.
 */
public enum FormationType {
    /** 2 ranks deep, wide frontage; maximizes frontal volley/melee width. */
    LINE(2, 1.8D, 1.8D),

    /** Dense, tight-packed defensive formation with overlapping shields and spears. */
    PHALANX(4, 1.1D, 1.1D),

    /** Hollow defensive box; guards against cavalry charges and flanking from all sides. */
    SQUARE(4, 2.0D, 2.0D),

    /** V-shaped breakthrough spearhead for cavalry shock assaults and line-splitting. */
    WEDGE(3, 1.5D, 1.5D),

    /** Dispersed, irregular order for archers/skirmishers; minimizes projectile vulnerability. */
    SKIRMISH(1, 3.0D, 3.0D);

    private final int defaultRanks;
    private final double fileSpacing;
    private final double rankSpacing;

    FormationType(int defaultRanks, double fileSpacing, double rankSpacing) {
        this.defaultRanks = defaultRanks;
        this.fileSpacing = fileSpacing;
        this.rankSpacing = rankSpacing;
    }

    public int defaultRanks() { return defaultRanks; }
    public double fileSpacing() { return fileSpacing; }
    public double rankSpacing() { return rankSpacing; }
}
