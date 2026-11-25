package com.eternalengine.civilization;

/**
 * How dense a sourced site is in a given year.
 * Village at −3000 Rakhigarhi; city at −2500 Mohenjo-daro.
 *
 * <p>COUNTRYSIDE (turn 231) is the honest answer where no settlement stands: a band of hunters,
 * fishers and gatherers. It exists because the old fallthrough invented VILLAGE, which dressed a
 * forager camp at Secunderabad as a market town in 3000 BC. When unsure, claim the least.
 */
public enum SettlementRank {
    COUNTRYSIDE(new String[] {"forager"}),
    VILLAGE(new String[] {"farmer"}),
    TOWN(new String[] {"farmer", "artisan"}),
    CITY(new String[] {"farmer", "artisan", "merchant", "citizen"});

    private final String[] roles;

    SettlementRank(String[] roles) {
        this.roles = roles;
    }

    public String[] roles() {
        return roles.clone();
    }

    public int npcCount() {
        return roles.length;
    }

    public static SettlementRank parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
