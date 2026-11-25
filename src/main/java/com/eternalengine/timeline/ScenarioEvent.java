package com.eternalengine.timeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One consequence inside an authored "what if" scenario.
 *
 * <p>A scenario event is a claim about a history that did not happen. It therefore must carry an
 * author-facing {@link Provenance} and, for {@link Provenance#AUTHORED} lines, ideally a source.
 *
 * <p>The shape mirrors the {@code AlternativeEvent} the engine already had — year, name,
 * description, form — plus the provenance and source this feature needs. It is deliberately data,
 * not behaviour: consequences are written, never generated at runtime.
 */
public final class ScenarioEvent {

    private final int year;
    private final AlternativeEvent.EventForm form;
    private final String name;
    private final String description;
    private final Provenance provenance;
    private final String source;
    private final Map<String, String> factionChanges;

    public ScenarioEvent(int year, AlternativeEvent.EventForm form, String name, String description,
                         Provenance provenance, String source, Map<String, String> factionChanges) {
        this.year = year;
        this.form = form == null ? AlternativeEvent.EventForm.NEW : form;
        this.name = name == null || name.isBlank() ? "Unnamed consequence" : name;
        this.description = description == null ? "" : description;
        this.provenance = provenance == null ? Provenance.MODELLED : provenance;
        this.source = source == null ? "" : source;
        this.factionChanges = Collections.unmodifiableMap(
                new LinkedHashMap<>(factionChanges == null ? Map.of() : factionChanges));
    }

    public int year() { return year; }
    public AlternativeEvent.EventForm form() { return form; }
    public String name() { return name; }
    public String description() { return description; }
    public Provenance provenance() { return provenance; }
    public String source() { return source; }
    public Map<String, String> factionChanges() { return factionChanges; }

    public boolean hasSource() {
        return !source.isBlank();
    }

    /** Convert to the engine's own event type so branches carry it without a parallel model. */
    public AlternativeEvent toAlternativeEvent() {
        return new AlternativeEvent(year, name, description, form, factionChanges);
    }

    /** True once the clock has reached this consequence. */
    public boolean dueIn(int currentYear) {
        return currentYear >= year;
    }
}
