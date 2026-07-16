package com.eternalengine.technology;

import java.util.List;

/**
 * Technology definition.
 */
public record Technology(String id, String name, TechEra era, int level, List<String> prerequisites) {
    public Technology {
        if (id == null || id.isBlank()) id = "tech_unknown";
        if (name == null || name.isBlank()) name = id;
        if (era == null) era = TechEra.ANCIENT;
        level = Math.max(0, level);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }
}
