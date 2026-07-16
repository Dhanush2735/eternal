package com.eternalengine.reputation;

import com.eternalengine.capability.IPlayerSimulationData;

import java.util.Map;

/**
 * Server-authoritative reputation applier.
 */
public final class ReputationEngine {

    public void initializeDefaults(IPlayerSimulationData data) {
        if (data == null) {
            return;
        }
        for (ReputationDimension dimension : ReputationDimension.values()) {
            if (dimension.polarity() == ReputationPolarity.POSITIVE) {
                readPositive(data, dimension);
            } else {
                readNegative(data, dimension);
            }
        }
    }

    public ReputationEffect applyAction(IPlayerSimulationData data, String actionType) {
        ReputationEffect effect = ActionReputationEffects.getEffect(actionType);
        applyEffect(data, effect);
        return effect;
    }

    public void applyEffect(IPlayerSimulationData data, ReputationEffect effect) {
        if (data == null || effect == null) {
            return;
        }
        for (Map.Entry<ReputationDimension, Integer> entry : effect.positiveChanges().entrySet()) {
            adjustPositive(data, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<ReputationDimension, Integer> entry : effect.negativeChanges().entrySet()) {
            adjustNegative(data, entry.getKey(), entry.getValue());
        }
    }

    public void adjustPositive(IPlayerSimulationData data, ReputationDimension dimension, int delta) {
        if (data == null || dimension == null || dimension.polarity() != ReputationPolarity.POSITIVE) {
            return;
        }
        int current = readPositive(data, dimension);
        writePositive(data, dimension, current + delta);
    }

    public void adjustNegative(IPlayerSimulationData data, ReputationDimension dimension, int delta) {
        if (data == null || dimension == null || dimension.polarity() != ReputationPolarity.NEGATIVE) {
            return;
        }
        int current = readNegative(data, dimension);
        writeNegative(data, dimension, current + delta);
    }

    public int overallPositive(IPlayerSimulationData data) {
        if (data == null) {
            return 0;
        }
        int sum = 0;
        int count = 0;
        for (ReputationDimension dimension : ReputationDimension.values()) {
            if (dimension.polarity() == ReputationPolarity.POSITIVE) {
                sum += readPositive(data, dimension);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    public int overallNegative(IPlayerSimulationData data) {
        if (data == null) {
            return 0;
        }
        int sum = 0;
        int count = 0;
        for (ReputationDimension dimension : ReputationDimension.values()) {
            if (dimension.polarity() == ReputationPolarity.NEGATIVE) {
                sum += readNegative(data, dimension);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    public int fearLevel(IPlayerSimulationData data) {
        if (data == null) {
            return 0;
        }
        int infamy = readNegative(data, ReputationDimension.INFAMY);
        int cruelty = readNegative(data, ReputationDimension.CRUELTY);
        int tyranny = readNegative(data, ReputationDimension.TYRANNY);
        return (infamy + cruelty + tyranny) / 3;
    }

    public int loyaltyLevel(IPlayerSimulationData data) {
        if (data == null) {
            return 0;
        }
        int military = readPositive(data, ReputationDimension.MILITARY);
        int personal = readPositive(data, ReputationDimension.PERSONAL);
        int political = readPositive(data, ReputationDimension.POLITICAL);
        int treachery = readNegative(data, ReputationDimension.TREACHERY);
        int cowardice = readNegative(data, ReputationDimension.COWARDICE);
        int base = (military + personal + political) / 3;
        return clamp(base - ((treachery + cowardice) / 4));
    }

    private int readPositive(IPlayerSimulationData data, ReputationDimension dimension) {
        int canonical = data.getPositiveRep(dimension.key());
        int legacy = data.getPositiveRep(dimension.legacyKey());
        int value = Math.max(canonical, legacy);
        writePositive(data, dimension, value);
        return value;
    }

    private int readNegative(IPlayerSimulationData data, ReputationDimension dimension) {
        int canonical = data.getNegativeRep(dimension.key());
        int legacy = data.getNegativeRep(dimension.legacyKey());
        int value = Math.max(canonical, legacy);
        writeNegative(data, dimension, value);
        return value;
    }

    private void writePositive(IPlayerSimulationData data, ReputationDimension dimension, int value) {
        int clamped = clamp(value);
        data.setPositiveRep(dimension.key(), clamped);
        data.setPositiveRep(dimension.legacyKey(), clamped);
    }

    private void writeNegative(IPlayerSimulationData data, ReputationDimension dimension, int value) {
        int clamped = clamp(value);
        data.setNegativeRep(dimension.key(), clamped);
        data.setNegativeRep(dimension.legacyKey(), clamped);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
