package com.eternalengine.timeline;

import com.eternalengine.core.WorldStateManager;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side timeline branch registry.
 *
 * <p>Owns the four things a divergence needs: the branches themselves, what each branch suppresses,
 * the chronicle that accumulates as the clock advances, and the authored scenarios that give a
 * branch its consequences.
 *
 * <p><b>Divergence is never automatic.</b> No simulation engine may call
 * {@link #createDivergence}. A branch exists if and only if a player intervened at an event, and
 * the world reaching a year on its own forks nothing.
 */
public final class TimelineManager {

    private final ConcurrentHashMap<String, TimelineBranch> branches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BranchChronicle> chronicles = new ConcurrentHashMap<>();

    /**
     * <b>The branch's own history.</b> {@link BranchChronicle} holds what the branch records about
     * the years the records cover; this holds what the world did in the years they do not. The two
     * are separate because they answer different questions: the chronicle annotates shipped history,
     * and this one <b>is</b> the history once the record runs out.
     */
    private final ConcurrentHashMap<String, Chronicler> chroniclers = new ConcurrentHashMap<>();

    /**
     * <b>What each player has actually done.</b> Step 49's part 2: the join step 48 named as missing, so a
     * player's role comes from their own history rather than from a field nobody sets. The five registers
     * that count these deeds call {@code deedLedger().sale(...)} and its siblings at the point the deed
     * happens; the ledger itself is Minecraft-free and tested on a plain JDK.
     */
    private final DeedLedger deeds = new DeedLedger();
    private final AlternativeTimelineEngine alternativeEngine;
    private final RealityRiftController riftController = new RealityRiftController();
    private final DivergenceCascadeEngine cascadeEngine = new DivergenceCascadeEngine(riftController);

    private final BranchSuppression suppression = new BranchSuppression();
    private ScenarioRegistry scenarios = new ScenarioRegistry();
    private SiblingIndex siblingIndex = SiblingIndex.build(List.of());

    /** Branch id -> scenario id, for branches that were created by an authored intervention. */
    private final ConcurrentHashMap<String, String> scenarioByBranch = new ConcurrentHashMap<>();

    /** Branch id -> the year the authored record ran out, already announced. */
    private final Set<String> handoffAnnounced = ConcurrentHashMap.newKeySet();

    private volatile String activeBranchId = "main";

    /** Last year the scenario pump ran; Integer.MIN_VALUE until the first tick. */
    private volatile int lastScenarioYear = Integer.MIN_VALUE;

    public TimelineManager() {
        this(new AlternativeTimelineEngine());
    }

    public TimelineManager(AlternativeTimelineEngine alternativeEngine) {
        this.alternativeEngine = alternativeEngine == null ? new AlternativeTimelineEngine() : alternativeEngine;
        TimelineBranch main = TimelineBranch.main(-3000);
        branches.put(main.branchId(), main);
        chronicles.put(main.branchId(), new BranchChronicle());
        chroniclers.put(main.branchId(), new Chronicler());
        suppression.register("main", null);
    }

    /**
     * Back to prime history only (step 14): every branch, chronicle and suppression gone. Called before a
     * world's branches are restored, because this manager outlives a world in single player (the forks of
     * the world played before were still registered in the next one), and by {@code /ee reset confirm}.
     */
    public void clearBranches() {
        branches.keySet().removeIf(id -> !"main".equals(id));
        chronicles.clear();
        chronicles.put("main", new BranchChronicle());
        chroniclers.put("main", new Chronicler());
        scenarioByBranch.clear();
        handoffAnnounced.clear();
        suppression.clear();
        activeBranchId = "main";
    }

    // ==================== wiring ====================

    /** Install the sibling index built from the history corpus at load time. */
    public void setSiblingIndex(SiblingIndex index) {
        if (index != null) {
            this.siblingIndex = index;
        }
    }

    public SiblingIndex siblingIndex() {
        return siblingIndex;
    }

    /** Install the authored scenarios loaded from disk. */
    public void setScenarios(ScenarioRegistry registry) {
        if (registry != null) {
            this.scenarios = registry;
        }
    }

    public ScenarioRegistry scenarios() {
        return scenarios;
    }

    public BranchSuppression suppression() {
        return suppression;
    }

    // ==================== persistence ====================

    /**
     * Rebuild the in-memory registry from a loaded world state.
     *
     * <p><b>This is the fix for forks vanishing on restart.</b> Previously nothing called anything
     * like this: {@code TimelineManager} was constructed fresh with only {@code main}, while
     * {@code WorldStateManager} happily restored {@code activeTimelineBranchId} to a branch that no
     * longer existed. {@link #getActiveBranch()} then fell back to prime through
     * {@code getOrDefault}, and the player's divergence disappeared with no error anywhere.
     */
    public int restoreFrom(WorldStateManager state) {
        if (state == null) {
            return 0;
        }
        Map<String, WorldStateManager.TimelineBranchState> saved = state.getAllTimelineBranches();
        if (saved.isEmpty()) {
            return 0;
        }

        int restored = 0;
        // Parents before children, so suppression ancestry resolves correctly at registration.
        List<WorldStateManager.TimelineBranchState> ordered = new ArrayList<>(saved.values());
        ordered.sort(java.util.Comparator.comparingInt(s -> ancestryDepth(s, saved)));

        for (WorldStateManager.TimelineBranchState s : ordered) {
            if (s == null || s.branchId == null || s.branchId.isBlank()) {
                continue;
            }
            if (s.isMain || "main".equals(s.branchId)) {
                suppression.register("main", null);
                continue;
            }

            TimelineBranch branch = new TimelineBranch(
                    s.branchId, s.parentId, s.divergenceEventId, s.divergenceYear,
                    s.cause, null, false);
            branch.setHistoryLogEntry(s.historyLogEntry);
            branches.put(branch.branchId(), branch);

            suppression.register(branch.branchId(), s.parentId == null ? "main" : s.parentId);
            suppression.suppressAll(branch.branchId(), s.suppressedEventIds);

            if (s.scenarioId != null && !s.scenarioId.isBlank()) {
                scenarioByBranch.put(branch.branchId(), s.scenarioId);
            }

            // Rebuild the chronicle from what the branch knows, so the Codex is not blank after a
            // restart even though consequence text is re-derived from the scenario on demand.
            BranchChronicle chronicle = chronicles.computeIfAbsent(branch.branchId(),
                    id -> new BranchChronicle());
            for (String pin : s.suppressedEventIds) {
                chronicle.recordAbsence(s.divergenceYear, pin, pin);
            }

            restored++;
        }

        String active = state.getActiveTimelineBranchId();
        if (active != null && branches.containsKey(active)) {
            activeBranchId = active;
        } else if (active != null) {
            // Should be unreachable: WorldStateManager already normalises this. Kept as a guard so
            // a divergence can never be silently swapped for prime without a trace.
            System.err.println("[TimelineManager] Active branch " + active
                    + " has no restored branch; staying on main");
            activeBranchId = "main";
        }
        return restored;
    }

    /**
     * Distance from {@code main} in the saved tree. Used only to sort parents ahead of children so
     * that, when a child registers its suppression, its ancestors are already registered.
     * Cycles in a corrupted save are bounded by the seen-set.
     */
    private static int ancestryDepth(WorldStateManager.TimelineBranchState s,
                                     Map<String, WorldStateManager.TimelineBranchState> saved) {
        int depth = 0;
        String current = s.parentId;
        Set<String> seen = new java.util.HashSet<>();
        while (current != null && seen.add(current)) {
            depth++;
            WorldStateManager.TimelineBranchState parent = saved.get(current);
            current = parent == null ? null : parent.parentId;
        }
        return depth;
    }

    // ==================== divergence ====================

    /**
     * Create a branch. <b>Only a player intervention may call this.</b>
     *
     * <p>Suppression of the pivot pin and its indexed siblings happens here. The runtime index
     * currently contains no automatically inferred siblings; geographic proximity is not identity.
     */
    public TimelineBranch createDivergence(TimelineSeed seed, String cause, WorldStateManager state) {
        TimelineBranch branch = alternativeEngine.generateAlternative(seed, state, cause);
        branches.put(branch.branchId(), branch);
        activeBranchId = branch.branchId();

        String parent = branch.parentBranchId() == null ? "main" : branch.parentBranchId();
        suppression.register(branch.branchId(), parent);
        chronicles.computeIfAbsent(branch.branchId(), id -> new BranchChronicle());

        // Suppress the pivot and its indexed siblings. The production index currently
        // has no inferred siblings; old saved suppression sets are restored independently.
        String pivot = branch.divergenceEventId();
        if (pivot != null && !pivot.isBlank()) {
            Set<String> cluster = siblingIndex.suppressionSetFor(pivot);
            suppression.suppressAll(branch.branchId(), cluster);

            BranchChronicle chronicle = chronicleFor(branch.branchId());
            chronicle.recordAbsence(branch.divergenceYear(), pivot, pivot);

            List<String> siblings = new ArrayList<>(cluster);
            siblings.remove(pivot);
            if (!siblings.isEmpty()) {
                chronicle.recordSiblings(branch.divergenceYear(), siblings);
            }

            // Attach an authored scenario if one exists, and record its moment-of-divergence entry.
            scenarios.forPin(pivot).ifPresent(def -> {
                scenarioByBranch.put(branch.branchId(), def.scenarioId());
                for (ScenarioEvent e : def.initialEvents()) {
                    branch.addEvent(e.toAlternativeEvent());
                    chronicle.add(e.year(), e.name(), e.description(), e.provenance());
                }
            });
        }

        if (state != null) {
            WorldStateManager.TimelineBranchState branchState = new WorldStateManager.TimelineBranchState();
            branchState.branchId = branch.branchId();
            branchState.parentId = branch.parentBranchId();
            branchState.divergenceEventId = branch.divergenceEventId();
            branchState.divergenceYear = branch.divergenceYear();
            branchState.cause = branch.divergenceCause();
            branchState.isMain = branch.isMainTimeline();
            branchState.historyLogEntry = branch.historyLogEntry();
            branchState.suppressedEventIds.addAll(suppression.suppressedHere(branch.branchId()));
            branchState.scenarioId = scenarioByBranch.getOrDefault(branch.branchId(), "");
            scenarios.byId(branchState.scenarioId)
                    .ifPresent(def -> branchState.authoredUntilYear = def.authoredUntilYear());

            state.addTimelineBranch(branchState);
            state.setActiveTimelineBranch(branch.branchId());
        }

        // The fork is live from this instant. Re-point the world's pin overlay at once, so the
        // consequences are visible immediately. Before this, the overlay was installed ONLY by
        // SimulationEngine.bootstrapDivergence at boot -- so an in-game fork changed the
        // bookkeeping but left the world looking exactly as it did, and the player who had just
        // stopped an event would still find it there. Every path into a fork (the typed commands,
        // the cascade, and the in-world intervention) comes through here, so the one call covers
        // all of them.
        installSuppressionOverlay();
        return branch;
    }

    /**
     * Point the world's pin overlay at the active branch: suppressed pins stop existing.
     *
     * <p>Idempotent, and safe before the gazetteer has loaded (the predicate is simply stored).
     * Called on fork, and again by {@code SimulationEngine.bootstrapDivergence} on boot so a
     * restored branch shows up in the world.
     */
    public void installSuppressionOverlay() {
        com.eternalengine.civilization.HistoryGazetteer.setSuppressionFilter(
                this::isSuppressedInActiveBranch);
    }

    // ==================== the scenario pump ====================

    /**
     * Advance the authored story to {@code currentYear}. Called once per simulated year.
     *
     * <p>This is what carries a divergence <em>to the end</em>. Nothing is pre-computed: each year
     * the pump asks which consequences have become true since the last one and records them.
     *
     * <p>Returns the number of consequences that fired.
     */
    public int tickScenarios(int currentYear, WorldStateManager state) {
        if (lastScenarioYear == Integer.MIN_VALUE) {
            lastScenarioYear = currentYear;
            return 0;
        }
        if (currentYear <= lastScenarioYear) {
            lastScenarioYear = currentYear;
            return 0;
        }
        int previous = lastScenarioYear;
        lastScenarioYear = currentYear;

        TimelineBranch active = getActiveBranch();
        if (active == null || active.isMainTimeline()) {
            return 0;
        }
        String scenarioId = scenarioByBranch.get(active.branchId());
        if (scenarioId == null) {
            return 0;
        }
        ScenarioDefinition def = scenarios.byId(scenarioId).orElse(null);
        if (def == null) {
            return 0;
        }

        BranchChronicle chronicle = chronicleFor(active.branchId());
        List<ScenarioEvent> due = def.eventsBetween(previous, currentYear);
        for (ScenarioEvent e : due) {
            active.addEvent(e.toAlternativeEvent());
            chronicle.add(e.year(), e.name(), e.description(), e.provenance());
            if (state != null && !e.factionChanges().isEmpty()) {
                for (Map.Entry<String, String> change : e.factionChanges().entrySet()) {
                    state.setFlag("branch." + active.branchId() + ".change."
                            + change.getKey() + "." + e.year(), change.getValue());
                }
            }
        }

        // Announce the handoff exactly once, so the player is told where written history stops.
        if (def.beyondAuthoredRecord(currentYear)
                && handoffAnnounced.add(active.branchId())) {
            chronicle.add(currentYear, "Beyond the written record",
                    "The authored consequences of this divergence end at " + def.authoredUntilYear()
                            + ". From here the branch continues on simulation alone — no further "
                            + "consequence in this timeline was written by a historian.",
                    Provenance.MODELLED);
        }
        return due.size();
    }

    /** Reset the pump, e.g. after a time jump or a branch switch. */
    public void resetScenarioCursor(int year) {
        lastScenarioYear = year;
    }

    // ==================== queries used by the overlay ====================

    /** True if this pin does not happen in the currently active branch. */
    public boolean isSuppressedInActiveBranch(String pinId) {
        return suppression.isSuppressed(activeBranchId, pinId);
    }

    /** Every pin that is gone in the currently active branch, ancestors included. */
    public Set<String> effectiveSuppressedInActiveBranch() {
        return suppression.effectiveSuppressed(activeBranchId);
    }

    /** The deed ledger, which every register that counts a deed writes to and a role is read from. */
    public DeedLedger deedLedger() {
        return deeds;
    }

    /** The recorder for a branch's own history. Created on demand, like its chronicle. */
    public Chronicler chroniclerFor(String branchId) {
        return chroniclers.computeIfAbsent(branchId, id -> new Chronicler());
    }

    /**
     * Report something the world did. Returns the line it became, or null if the recorder judged it
     * not worth a line, which is the common case: most of what happens is not history.
     *
     * <p>Nothing in the engine calls this yet. That is deliberate and it is written down rather than
     * hidden: the recorder is built and tested, and the wiring from the simulation's own events into
     * it is the next step's work.
     */
    @Nullable
    public Chronicler.Line record(String branchId, Chronicler.Change change) {
        return chroniclerFor(branchId).report(change);
    }

    public BranchChronicle chronicleFor(String branchId) {
        return chronicles.computeIfAbsent(branchId == null ? "main" : branchId,
                id -> new BranchChronicle());
    }

    public BranchChronicle activeChronicle() {
        return chronicleFor(activeBranchId);
    }

    /** Scenario attached to the active branch, if any. */
    public Optional<ScenarioDefinition> activeScenario() {
        String id = scenarioByBranch.get(activeBranchId);
        return id == null ? Optional.empty() : scenarios.byId(id);
    }

    /** One-line status for the Codex and /ee commands. */
    public String statusLine() {
        TimelineBranch active = getActiveBranch();
        if (active == null || active.isMainTimeline()) {
            return "§2Prime Continuum§r — no divergence";
        }
        String scenarioNote = activeScenario()
                .map(s -> " §7(scenario: " + s.scenarioId() + ")§r")
                .orElse(" §7(no authored scenario — consequences are modelled)§r");
        return "§cAlternate Reality§r since §f" + active.divergenceYear() + "§r "
                + suppression.effectiveSuppressed(active.branchId()).size() + " pin(s) suppressed"
                + scenarioNote;
    }

    // ==================== existing accessors ====================

    public TimelineBranch getActiveBranch() {
        TimelineBranch b = branches.get(activeBranchId);
        return b != null ? b : branches.get("main");
    }

    public Optional<TimelineBranch> getBranch(String branchId) {
        return Optional.ofNullable(branches.get(branchId));
    }

    public TimelineBranch getMainTimeline() {
        return branches.get("main");
    }

    public List<TimelineBranch> getDivergencePoints() {
        List<TimelineBranch> list = new ArrayList<>();
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

    public RealityRiftController getRiftController() {
        return riftController;
    }

    public DivergenceCascadeEngine getCascadeEngine() {
        return cascadeEngine;
    }
}
