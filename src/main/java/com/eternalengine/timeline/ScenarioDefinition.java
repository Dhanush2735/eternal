package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An authored "what if this was prevented" story.
 *
 * <p>A scenario is a file, not a system. It names the pin that triggers it, and lists the
 * consequences that follow — each stamped with the year it becomes true. Nothing here is computed
 * at play time; the world simply walks forward into a story somebody wrote.
 *
 * <p><b>Coverage is bounded on purpose.</b> {@link #authoredUntilYear()} marks where the written
 * record stops. Beyond it the branch continues on simulation, and the chronicle says so. A
 * scenario for 26/11 can plausibly be written to the present; one for 44 BC cannot, and must not
 * pretend otherwise.
 */
public final class ScenarioDefinition {

    private final String scenarioId;
    private final String triggerPin;
    private final int triggerYear;
    private final String title;
    private final String tagline;
    private final String author;
    private final Provenance provenance;
    private final int authoredUntilYear;
    private final String epilogue;
    private final List<ScenarioEvent> events;

    public ScenarioDefinition(String scenarioId, String triggerPin, int triggerYear, String title,
                              String tagline, String author, Provenance provenance,
                              int authoredUntilYear, String epilogue, List<ScenarioEvent> events) {
        this.scenarioId = scenarioId == null ? "" : scenarioId;
        this.triggerPin = triggerPin == null ? "" : triggerPin;
        this.triggerYear = triggerYear;
        this.title = title == null || title.isBlank() ? "Untitled scenario" : title;
        this.tagline = tagline == null ? "" : tagline;
        this.author = author == null ? "" : author;
        this.provenance = provenance == null ? Provenance.AUTHORED : provenance;
        this.authoredUntilYear = authoredUntilYear;
        this.epilogue = epilogue == null ? "" : epilogue;

        List<ScenarioEvent> sorted = new ArrayList<>(events == null ? List.of() : events);
        sorted.sort(java.util.Comparator.comparingInt(ScenarioEvent::year));
        this.events = Collections.unmodifiableList(sorted);
    }

    public String scenarioId() { return scenarioId; }
    public String triggerPin() { return triggerPin; }
    public int triggerYear() { return triggerYear; }
    public String title() { return title; }
    public String tagline() { return tagline; }
    public String author() { return author; }
    public Provenance provenance() { return provenance; }
    public int authoredUntilYear() { return authoredUntilYear; }
    public String epilogue() { return epilogue; }
    public List<ScenarioEvent> events() { return events; }

    public boolean isUsable() {
        return !scenarioId.isBlank() && !triggerPin.isBlank();
    }

    public boolean hasAuthor() {
        return !author.isBlank();
    }

    /**
     * Consequences that are true <em>at the moment of divergence</em> — the entry for the trigger
     * year itself.
     *
     * <p>These must be recorded by the caller when the fork is created, not by
     * {@link #eventsBetween} later. {@code eventsBetween} uses an exclusive lower bound so that
     * crossing a year fires each consequence exactly once; consequently the trigger-year entry
     * can never be reached through it. Without this method that entry would be silently lost.
     */
    public List<ScenarioEvent> initialEvents() {
        List<ScenarioEvent> out = new ArrayList<>();
        for (ScenarioEvent e : events) {
            int y = e.year() == 0 ? triggerYear : e.year();
            if (y <= triggerYear) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Consequences that have become true by {@code currentYear} and were not true before it.
     * The caller passes the previous year so each line fires exactly once as the clock crosses it.
     *
     * <p>Lower bound is exclusive: {@code triggerYear} is not included — see
     * {@link #initialEvents()}.
     */
    public List<ScenarioEvent> eventsBetween(int previousYear, int currentYear) {
        List<ScenarioEvent> out = new ArrayList<>();
        for (ScenarioEvent e : events) {
            int y = e.year() == 0 ? triggerYear : e.year();
            if (y > previousYear && y <= currentYear) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** True once the branch has moved past everything the author actually wrote. */
    public boolean beyondAuthoredRecord(int currentYear) {
        return authoredUntilYear > 0 && currentYear > authoredUntilYear;
    }

    public int consequenceCount() {
        return events.size();
    }
}
