package com.eternalengine.timeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One event inside an alternative timeline branch.
 */
public final class AlternativeEvent {

    public enum EventForm {
        NEW,
        DIVERGED,
        DELAYED,
        PREVENTED_CONSEQUENCE
    }

    private final int year;
    private final String eventName;
    private final String description;
    private final EventForm form;
    private final Map<String, String> factionChanges;

    public AlternativeEvent(int year, String eventName, String description, EventForm form,
                            Map<String, String> factionChanges) {
        this.year = year;
        this.eventName = eventName == null || eventName.isBlank() ? "Unnamed alternative event" : eventName;
        this.description = description == null ? "" : description;
        this.form = form == null ? EventForm.NEW : form;
        this.factionChanges = Collections.unmodifiableMap(new HashMap<>(factionChanges == null ? Map.of() : factionChanges));
    }

    public int year() { return year; }
    public String eventName() { return eventName; }
    public String description() { return description; }
    public EventForm form() { return form; }
    public Map<String, String> factionChanges() { return factionChanges; }
}
