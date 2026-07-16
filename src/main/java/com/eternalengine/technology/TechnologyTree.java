package com.eternalengine.technology;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Technology tree foundation.
 */
public final class TechnologyTree {
    private final ConcurrentHashMap<String, Technology> technologies = new ConcurrentHashMap<>();

    public TechnologyTree() {
        registerDefaults();
    }

    public void register(Technology technology) {
        if (technology != null) technologies.put(technology.id(), technology);
    }

    public Optional<Technology> get(String id) {
        return Optional.ofNullable(technologies.get(id));
    }

    public Collection<Technology> all() {
        return java.util.Collections.unmodifiableCollection(technologies.values());
    }

    private void registerDefaults() {
        register(new Technology("agriculture", "Agriculture", TechEra.ANCIENT, 1, java.util.List.of()));
        register(new Technology("writing", "Writing", TechEra.ANCIENT, 2, java.util.List.of("agriculture")));
        register(new Technology("steel", "Steel Working", TechEra.MEDIEVAL, 3, java.util.List.of("writing")));
        register(new Technology("printing_press", "Printing Press", TechEra.RENAISSANCE, 4, java.util.List.of("writing")));
        register(new Technology("steam_power", "Steam Power", TechEra.INDUSTRIAL, 6, java.util.List.of("steel")));
        register(new Technology("electricity", "Electricity", TechEra.INDUSTRIAL, 7, java.util.List.of("steam_power")));
        register(new Technology("computing", "Computing", TechEra.MODERN, 9, java.util.List.of("electricity")));
    }
}
