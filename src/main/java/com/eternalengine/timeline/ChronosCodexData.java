package com.eternalengine.timeline;

import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model providing structured parchment book pages for the Chronos Codex GUI.
 * Covers Timeline Tree, Geopolitical Atlas, VIP Dossiers, and Historical Chronicles.
 */
public final class ChronosCodexData {

    public enum CodexTab {
        TIMELINE_TREE,
        ATLAS,
        DOSSIER,
        CHRONICLE
    }

    public static List<String> buildTimelineTreePages(TimelineManager timelineManager) {
        List<String> pages = new ArrayList<>();
        TimelineBranch active = timelineManager != null ? timelineManager.getActiveBranch() : null;
        if (active == null) {
            pages.add("§6=== THE CHRONOS CODEX ===§r\n\n§8Prime Timeline: Invariant§r\nNo active branches recorded.");
            return pages;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6=== TIMELINE CODEX ===§r\n\n");
        sb.append("§1Branch:§r ").append(active.branchId()).append("\n");
        sb.append("§1Status:§r ").append(active.isMainTimeline() ? "§2Prime Continuum§r" : "§cAlternate Reality§r").append("\n");
        if (!active.isMainTimeline()) {
            sb.append("§1Divergence:§r ").append(active.divergenceYear()).append("\n");
            sb.append("§1Pivot Event:§r ").append(active.divergenceEventId()).append("\n");
            sb.append("§1Cause:§r ").append(active.divergenceCause()).append("\n");
        }
        sb.append("\n§8Pruned Events:§r ").append(active.permanentlyPrevented().size());
        sb.append("\n§8Alternate Events:§r ").append(active.events().size());
        pages.add(sb.toString());

        if (!active.events().isEmpty()) {
            StringBuilder eb = new StringBuilder();
            eb.append("§6=== ALTERNATE EVENTS ===§r\n\n");
            for (AlternativeEvent ae : active.events()) {
                eb.append("§2[").append(ae.year()).append("]§r §l").append(ae.eventName()).append("§r\n");
                eb.append("§7").append(ae.description()).append("§r\n\n");
            }
            pages.add(eb.toString());
        }

        return pages;
    }

    public static List<String> buildAtlasPages(WorldStateManager state, int currentYear) {
        List<String> pages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== GEOPOLITICAL ATLAS ===§r\n");
        sb.append("§8Epoch: ").append(currentYear < 0 ? Math.abs(currentYear) + " BCE" : currentYear + " CE").append("§r\n\n");

        if (state != null) {
            var civs = state.getAllCivilizationIds();
            sb.append("§1Civilizations Known:§r ").append(civs.size()).append("\n");
            for (String civId : civs) {
                var civ = state.getCivilization(civId);
                if (civ != null) {
                    sb.append(" • §l").append(civId.toUpperCase(java.util.Locale.ROOT)).append("§r\n");
                    sb.append("   Pop: ").append(civ.population).append(" | Treasury: ").append(civ.treasury).append("\n");
                }
            }
        } else {
            sb.append("No geopolitical cartographic records loaded.");
        }
        pages.add(sb.toString());
        return pages;
    }

    public static List<String> buildDossierPages() {
        List<String> pages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== VIP DOSSIER ===§r\n\n");
        sb.append("§lJulius Caesar§r (100–44 BCE)\n§7Dictator of Rome. Pivot point of the Roman Republic.§r\n\n");
        sb.append("§lFranz Ferdinand§r (1863–1914)\n§7Archduke of Austria-Este. Sarajevo convergence point.§r\n\n");
        sb.append("§lQin Shi Huang§r (259–210 BCE)\n§7First Emperor of unified China. Great Wall builder.§r\n");
        pages.add(sb.toString());
        return pages;
    }

    /**
     * No timeline was supplied, so the gazette has nothing it is entitled to report.
     *
     * <p>Kept because the client codex screen has no view of the server's timeline. It says so
     * rather than guessing, because a gazette that guesses is worse than one that admits it does
     * not know.
     */
    public static List<String> buildChroniclePages(int year) {
        return buildChroniclePages(null, year);
    }

    /**
     * The gazette as the player actually reads it.
     *
     * <p>This used to print three invented sentences — "Diplomatic embassies exchange envoys across
     * imperial borders" — for every year, on every timeline, forever. A player who had just stopped
     * the 26/11 attack and created an alternate world would open the gazette and read state-of-the-
     * world boilerplate that had nothing to do with anything they had done. That is exactly the
     * fabricated chronicle this project forbids.
     *
     * <p>It now prints the branch's own chronicle: only what actually happened in <em>this</em>
     * world, each line labelled with where it came from, and an honest statement of where written
     * history stops. When there is nothing to report it says so instead of inventing a report.
     *
     * @param timelineManager the live timeline; null if this side of the game has no simulation
     * @param year            the year the player is asking about
     */
    public static List<String> buildChroniclePages(TimelineManager timelineManager, int year) {
        List<String> pages = new ArrayList<>();

        if (timelineManager == null) {
            pages.add("§6=== THE HISTORICAL GAZETTE ===§r\n\n"
                    + "§8No timeline is running on this side of the game.§r\n\n"
                    + "Nothing is reported here, because nothing here can be known to be true. "
                    + "Run §r§l/ee chronicle§r in game to read the chronicle of your own timeline.");
            return pages;
        }

        TimelineBranch active = timelineManager.getActiveBranch();
        boolean onRecordedTimeline = active == null || active.isMainTimeline();

        if (onRecordedTimeline) {
            pages.add("§6=== THE HISTORICAL GAZETTE ===§r\n"
                    + "§8Year " + yearText(year) + "§r\n\n"
                    + "Nothing has been changed. This is the recorded timeline: the events of "
                    + "history happened where and when the record says they did, and every "
                    + "invention, building and discovery in this world is the real one.\n\n"
                    + "§8Stop one of them — stand where it happens, in the year it happens, and "
                    + "fight off what causes it — and this page becomes the story of what happened "
                    + "instead.§r");
            return pages;
        }

        List<BranchChronicle.Entry> entries =
                timelineManager.activeChronicle().upTo(year);

        StringBuilder head = new StringBuilder();
        head.append("§6=== THE HISTORICAL GAZETTE ===§r\n");
        head.append("§8Year ").append(yearText(year)).append("§r\n\n");
        head.append("§1Timeline:§r §cnot the recorded one§r\n");
        head.append("§1Diverged:§r ").append(active.divergenceYear()).append(" — ")
                .append(pinName(active.divergenceEventId())).append(" did not happen.\n");
        head.append("§1Cause:§r ").append(active.divergenceCause()).append("\n\n");
        head.append("§8Everything below is what followed, in this world.§r");
        pages.add(head.toString());

        if (entries.isEmpty()) {
            pages.add("§6=== WHAT FOLLOWED ===§r\n\n"
                    + "§8Nothing yet. The divergence has been recorded, but no consequence of it "
                    + "has reached this year.§r");
            return pages;
        }

        // Six entries a page, so a long chronicle reads as a gazette rather than a wall.
        StringBuilder sb = new StringBuilder("§6=== WHAT FOLLOWED ===§r\n\n");
        int onPage = 0;
        for (BranchChronicle.Entry e : entries) {
            sb.append("§2[").append(e.year()).append("]§r §l").append(e.title()).append("§r\n");
            sb.append("§7").append(e.provenance().colour()).append(e.provenance().label()).append("§r");
            if (e.hasSource()) {
                sb.append(" §8· §r§7").append(e.source()).append("§r");
            }
            if (!e.body().isBlank()) {
                sb.append("\n§7").append(e.body()).append("§r");
            }
            sb.append("\n\n");
            if (++onPage == 6) {
                pages.add(sb.toString());
                sb = new StringBuilder("§6=== WHAT FOLLOWED (continued) ===§r\n\n");
                onPage = 0;
            }
        }
        sb.append("§8").append(timelineManager.activeChronicle().trustSummary()).append("§r");
        pages.add(sb.toString());
        return pages;
    }

    /**
     * The player-facing name of a pin, or the raw id if the corpus cannot resolve it.
     *
     * <p>{@code allEvents()} is the raw corpus and is deliberately not run through the suppression
     * filter, so the event the player just erased can still be named on the page that reports its
     * erasure.
     */
    private static String pinName(String pinId) {
        if (pinId == null || pinId.isBlank()) {
            return "(unknown)";
        }
        for (com.eternalengine.civilization.HistoryGazetteer.Event e
                : com.eternalengine.civilization.HistoryGazetteer.get().allEvents()) {
            if (pinId.equals(e.id())) {
                return e.name();
            }
        }
        return pinId;
    }

    private static String yearText(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }
}
