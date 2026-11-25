package com.eternalengine.core;

import com.eternalengine.civilization.CivilizationManager;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.LeaderRegister;
import com.eternalengine.civilization.RulerRegister;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Step 26 (system A1): the one query every system uses. Each question carries a {@link WorldKey}: place, date,
 * timeline and universe.
 *
 * <ul>
 *   <li><b>Universe.</b> Recorded history lives in the prime universe (the overworld). Any other universe has no
 *       recorded history: nobody, no ruler, no event (systems H will give a broken universe its own).</li>
 *   <li><b>Timeline.</b> Events a timeline prevented are gone in that timeline and its children, and only there. The
 *       key's own timeline is asked, not the active one, so the main timeline can be read while a branch is active
 *       (a history book of what "really" happened, a check).</li>
 *   <li><b>Facts that do not change with the timeline yet</b> (who rules, the head of state, money, languages): the
 *       record answers. When a timeline can change them (systems G: what-if, rulers who decide), the change goes
 *       here, in one place, and every system sees it.</li>
 * </ul>
 *
 * <p>Answers are the same as the layer below for the record itself (main timeline, prime universe); the check part
 * "key" (/ee check all) proves it at the world-wide test places.
 */
public final class WorldQuery {

    private WorldQuery() {}

    /** Everything recorded or estimated at the key. */
    public static HistoricalPresence.Result presence(CivilizationManager manager, WorldKey key) {
        if (!key.isPrime() || !key.placed()) {
            return HistoricalPresence.nobody(key.lat(), key.lon(), key.year());
        }
        return HistoricalPresence.query(manager, key.lat(), key.lon(), key.year(), hiddenPins(key.timeline()));
    }

    /** The same, with an explicit list of prevented events (the check uses it; game code passes a key). */
    public static HistoricalPresence.Result presence(CivilizationManager manager, WorldKey key, Predicate<String> hidden) {
        if (!key.isPrime() || !key.placed()) {
            return HistoricalPresence.nobody(key.lat(), key.lon(), key.year());
        }
        return HistoricalPresence.query(manager, key.lat(), key.lon(), key.year(), hidden);
    }

    /** Who rules the land at the key, with the recorded/estimated mark ({@link RulerRegister.Hit#estimated}). */
    public static Optional<RulerRegister.Hit> ruler(WorldKey key) {
        if (!key.isPrime() || !key.placed()) {
            return Optional.empty();
        }
        return com.eternalengine.civilization.WhoRuled.hit(key.lat(), key.lon(), key.year());
    }

    /** The head of state at the key (marked estimated when the list guessed the reign's end). */
    public static Optional<LeaderRegister.Reign> head(WorldKey key) {
        return ruler(key).flatMap(r -> LeaderRegister.get().facts(r.wikidata()))
                .flatMap(f -> f.headIn(key.year()));
    }

    /** The nearest event at the key, in the key's timeline. */
    public static Optional<HistoryGazetteer.EventHit> event(WorldKey key) {
        if (!key.isPrime() || !key.placed()) {
            return Optional.empty();
        }
        return HistoryGazetteer.get().nearestEvent(key.lat(), key.lon(), key.year(), hiddenPins(key.timeline()));
    }

    /**
     * The events a timeline prevented: its own and its parents' (step 14's suppression list). The active timeline
     * uses the gazetteer's installed filter (the same list, already wired); main prevents nothing.
     */
    public static Predicate<String> hiddenPins(String timeline) {
        if (timeline == null || WorldKey.MAIN.equals(timeline)) {
            return id -> false;
        }
        try {
            if (SimulationEngine.isInitialized()) {
                var tm = SimulationEngine.getInstance().getTimelineManager();
                if (tm != null) {
                    if (timeline.equals(tm.activeBranchId())) {
                        return HistoryGazetteer.suppressionFilter();
                    }
                    var suppression = tm.suppression();
                    return id -> suppression.isSuppressed(timeline, id);
                }
            }
        } catch (RuntimeException ignored) {
            // engine shutting down: fall through
        }
        return HistoryGazetteer.suppressionFilter();
    }
}
