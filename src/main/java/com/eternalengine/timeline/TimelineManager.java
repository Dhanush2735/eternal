package com.eternalengine.timeline;

import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side timeline branch registry foundation.
 */
public final class TimelineManager {

    private final ConcurrentHashMap<String, TimelineBranch> branches = new ConcurrentHashMap<>();
    private final AlternativeTimelineEngine alternativeEngine;
    private volatile String activeBranchId = "main";

    public TimelineManager() {
        this(new AlternativeTimelineEngine());
    }

    public TimelineManager(AlternativeTimelineEngine alternativeEngine) {
        this.alternativeEngine = alternativeEngine == null ? new AlternativeTimelineEngine() : alternativeEngine;
        TimelineBranch main = TimelineBranch.main(-3000);
        branches.put(main.branchId(), main);
    }

    public TimelineBranch createDivergence(TimelineSeed seed, String cause, WorldStateManager state) {
        TimelineBranch branch = alternativeEngine.generateAlternative(seed, state, cause);
        branches.put(branch.branchId(), branch);
        activeBranchId = branch.branchId();
        if (state != null) {
            WorldStateManager.TimelineBranchState branchState = new WorldStateManager.TimelineBranchState();
            branchState.branchId = branch.branchId();
            branchState.parentId = branch.parentBranchId();
            branchState.divergenceEventId = branch.divergenceEventId();
            branchState.divergenceYear = branch.divergenceYear();
            branchState.cause = branch.divergenceCause();
            branchState.isMain = branch.isMainTimeline();
            state.addTimelineBranch(branchState);
            state.setActiveTimelineBranch(branch.branchId());
        }
        return branch;
    }

    public TimelineBranch getActiveBranch() {
        return branches.getOrDefault(activeBranchId, branches.get("main"));
    }

    public Optional<TimelineBranch> getBranch(String branchId) {
        return Optional.ofNullable(branches.get(branchId));
    }

    public TimelineBranch getMainTimeline() {
        return branches.get("main");
    }

    public java.util.List<TimelineBranch> getDivergencePoints() {
        java.util.List<TimelineBranch> list = new ArrayList<>();
        for (TimelineBranch branch : branches.values()) {
            if (!branch.isMainTimeline()) {
                list.add(branch);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public String activeBranchId() {
        return activeBranchId;
    }

    public AlternativeTimelineEngine alternativeEngine() {
        return alternativeEngine;
    }
}
