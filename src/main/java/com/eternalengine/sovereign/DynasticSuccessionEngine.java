package com.eternalengine.sovereign;

import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynastic Succession & Coup Engine.
 * Manages royal bloodlines, heir generation, succession laws (primogeniture, elective senate),
 * and pretender usurpation coups.
 */
public final class DynasticSuccessionEngine {

    public enum SuccessionLaw {
        PRIMOGENITURE,
        ELECTIVE_SENATE,
        SENIORITY,
        USURPATION_COUP
    }

    public record DynasticHeir(
        String heirId,
        String civId,
        String name,
        double claimStrength,
        int legitimacy,
        int birthYear
    ) {}

    private final ConcurrentHashMap<String, List<DynasticHeir>> dynasticHeirs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SuccessionLaw> civLaws = new ConcurrentHashMap<>();

    public void setSuccessionLaw(String civId, SuccessionLaw law) {
        if (civId != null && law != null) {
            civLaws.put(civId.toLowerCase(Locale.ROOT), law);
        }
    }

    public SuccessionLaw getSuccessionLaw(String civId) {
        if (civId == null) return SuccessionLaw.PRIMOGENITURE;
        return civLaws.getOrDefault(civId.toLowerCase(Locale.ROOT), SuccessionLaw.PRIMOGENITURE);
    }

    public DynasticHeir registerHeir(String civId, String name, double claimStrength, int legitimacy, int birthYear) {
        if (civId == null || name == null) return null;

        String id = "heir_" + civId + "_" + System.currentTimeMillis() % 10000;
        DynasticHeir heir = new DynasticHeir(id, civId, name, claimStrength, legitimacy, birthYear);

        dynasticHeirs.computeIfAbsent(civId.toLowerCase(Locale.ROOT), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(heir);
        return heir;
    }

    /**
     * Resolves sovereign succession upon ruler death according to civilization law.
     */
    public DynasticHeir evaluateSuccession(String civId, String deceasedRulerId, int currentYear, WorldStateManager state) {
        if (civId == null) return null;
        List<DynasticHeir> heirs = dynasticHeirs.get(civId.toLowerCase(Locale.ROOT));
        if (heirs == null || heirs.isEmpty()) {
            // Generate a emergency cadet branch heir
            DynasticHeir generated = registerHeir(civId, "Regent Prince " + civId.toUpperCase(Locale.ROOT), 0.70D, 65, currentYear - 25);
            if (state != null) {
                state.setFlag("dynasty.ruler." + civId, generated.name());
            }
            return generated;
        }

        SuccessionLaw law = getSuccessionLaw(civId);
        DynasticHeir successor;

        if (law == SuccessionLaw.PRIMOGENITURE) {
            // Eldest heir with highest claim
            successor = heirs.stream()
                .min(Comparator.comparingInt(DynasticHeir::birthYear))
                .orElse(heirs.get(0));
        } else if (law == SuccessionLaw.ELECTIVE_SENATE) {
            // Heir with highest legitimacy
            successor = heirs.stream()
                .max(Comparator.comparingInt(DynasticHeir::legitimacy))
                .orElse(heirs.get(0));
        } else {
            successor = heirs.get(0);
        }

        if (state != null) {
            state.setFlag("dynasty.ruler." + civId, successor.name());
            state.setFlag("dynasty.last_succession." + civId, Integer.toString(currentYear));
        }

        return successor;
    }

    /**
     * Executes a palace coup or military usurpation attempting to seize the throne.
     */
    public boolean attemptUsurpation(String civId, String pretenderName, boolean militaryBacked, WorldStateManager state) {
        if (civId == null || pretenderName == null) return false;

        double baseOdds = militaryBacked ? 0.75D : 0.35D;
        boolean success = Math.random() < baseOdds;

        if (success) {
            DynasticHeir usurper = registerHeir(civId, pretenderName, 0.95D, 40, state != null ? state.getCurrentYear() - 30 : 0);
            if (state != null) {
                state.setFlag("dynasty.ruler." + civId, usurper.name());
                state.setFlag("dynasty.coup_occurred." + civId, "true");
            }
            return true;
        } else {
            if (state != null) {
                state.setFlag("dynasty.failed_coup." + civId, pretenderName);
            }
            return false;
        }
    }

    public List<DynasticHeir> getHeirs(String civId) {
        if (civId == null) return List.of();
        List<DynasticHeir> list = dynasticHeirs.get(civId.toLowerCase(Locale.ROOT));
        return list != null ? List.copyOf(list) : List.of();
    }
}
