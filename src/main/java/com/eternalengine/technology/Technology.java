package com.eternalengine.technology;

import java.util.List;

/**
 * Technology definition.
 *
 * <p>Step 21: every technology has the first year it is known anywhere on Earth ({@code firstYear}, negative before
 * the common era) and where ({@code firstPlace}). The engine never unlocks a technology before that year, and takes it
 * away again when the date is moved back before it. Where and when it spread from there is the technology atlas's work
 * (step 30a); this is only the floor: nobody anywhere has it earlier.
 */
public record Technology(String id, String name, TechEra era, int level, List<String> prerequisites,
                         int firstYear, String firstPlace) {

    /** No known first year: always allowed (for technologies registered by other code without a date). */
    public static final int ALWAYS = Integer.MIN_VALUE;

    public Technology {
        if (id == null || id.isBlank()) id = "tech_unknown";
        if (name == null || name.isBlank()) name = id;
        if (era == null) era = TechEra.ANCIENT;
        level = Math.max(0, level);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        firstPlace = firstPlace == null ? "" : firstPlace;
    }

    /** The old form, without a date: always allowed. */
    public Technology(String id, String name, TechEra era, int level, List<String> prerequisites) {
        this(id, name, era, level, prerequisites, ALWAYS, "");
    }

    /** Whether anybody on Earth has it in this year. */
    public boolean knownIn(int year) {
        return firstYear == ALWAYS || year >= firstYear;
    }

    /** "1440 (Mainz)", "3200 BCE (Sumer and Egypt)". */
    public String firstLabel() {
        if (firstYear == ALWAYS) {
            return "always";
        }
        String y = firstYear < 0 ? (-firstYear) + " BCE" : String.valueOf(firstYear);
        return firstPlace.isBlank() ? y : y + " (" + firstPlace + ")";
    }
}
