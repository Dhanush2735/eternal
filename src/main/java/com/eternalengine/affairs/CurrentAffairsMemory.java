package com.eternalengine.affairs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small bounded in-memory history of generated briefings.
 * Persistence can be added later through PersistenceManager.
 */
public final class CurrentAffairsMemory {

    private final ConcurrentHashMap<String, Deque<CurrentAffairsBriefing>> memory = new ConcurrentHashMap<>();
    private final int maxPerRegion;

    public CurrentAffairsMemory() {
        this(20);
    }

    public CurrentAffairsMemory(int maxPerRegion) {
        this.maxPerRegion = Math.max(1, Math.min(200, maxPerRegion));
    }

    public void remember(CurrentAffairsBriefing briefing) {
        if (briefing == null) {
            return;
        }
        Deque<CurrentAffairsBriefing> deque = memory.computeIfAbsent(briefing.regionId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(briefing);
            while (deque.size() > maxPerRegion) {
                deque.removeFirst();
            }
        }
    }

    public List<CurrentAffairsBriefing> recent(String regionId, int count) {
        Deque<CurrentAffairsBriefing> deque = memory.get(regionId == null ? "global" : regionId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            List<CurrentAffairsBriefing> all = new ArrayList<>(deque);
            int from = Math.max(0, all.size() - Math.max(0, count));
            return List.copyOf(all.subList(from, all.size()));
        }
    }

    public void clear() {
        memory.clear();
    }
}
