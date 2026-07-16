package com.eternalengine.reputation;

import java.util.Locale;

/**
 * Canonical Eternal Engine reputation dimensions.
 *
 * Existing capability data may contain legacy REP_/NEG_ prefixes; use key() and
 * legacyKey() when reading/writing until the save format is migrated.
 */
public enum ReputationDimension {
    MILITARY(ReputationPolarity.POSITIVE),
    POLITICAL(ReputationPolarity.POSITIVE),
    ECONOMIC(ReputationPolarity.POSITIVE),
    RELIGIOUS(ReputationPolarity.POSITIVE),
    CULTURAL(ReputationPolarity.POSITIVE),
    PERSONAL(ReputationPolarity.POSITIVE),
    SCIENTIFIC(ReputationPolarity.POSITIVE),
    DIPLOMATIC(ReputationPolarity.POSITIVE),

    INFAMY(ReputationPolarity.NEGATIVE),
    COWARDICE(ReputationPolarity.NEGATIVE),
    TREACHERY(ReputationPolarity.NEGATIVE),
    CORRUPTION(ReputationPolarity.NEGATIVE),
    CRUELTY(ReputationPolarity.NEGATIVE),
    INCOMPETENCE(ReputationPolarity.NEGATIVE),
    HERESY(ReputationPolarity.NEGATIVE),
    DISHONOR(ReputationPolarity.NEGATIVE),
    TYRANNY(ReputationPolarity.NEGATIVE),
    GREED(ReputationPolarity.NEGATIVE),
    WEAKNESS(ReputationPolarity.NEGATIVE),
    PARANOIA(ReputationPolarity.NEGATIVE),
    HUBRIS(ReputationPolarity.NEGATIVE),
    INSTABILITY(ReputationPolarity.NEGATIVE);

    private final ReputationPolarity polarity;

    ReputationDimension(ReputationPolarity polarity) {
        this.polarity = polarity;
    }

    public ReputationPolarity polarity() {
        return polarity;
    }

    public String key() {
        return name();
    }

    public String legacyKey() {
        return polarity == ReputationPolarity.POSITIVE ? "REP_" + name() : "NEG_" + name();
    }

    public static ReputationDimension parse(String value) {
        if (value == null || value.isBlank()) {
            return PERSONAL;
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("REP_")) {
            cleaned = cleaned.substring(4);
        } else if (cleaned.startsWith("NEG_")) {
            cleaned = cleaned.substring(4);
        }
        try {
            return ReputationDimension.valueOf(cleaned);
        } catch (IllegalArgumentException ignored) {
            return PERSONAL;
        }
    }
}
