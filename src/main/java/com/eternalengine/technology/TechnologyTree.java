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

    /**
     * The first known year of each, anywhere on Earth (step 21). Sources: Wikipedia's articles on each (agriculture:
     * the Fertile Crescent about 9500 BCE; writing: Sumerian cuneiform and Egyptian hieroglyphs about 3200 BCE; steel:
     * Kaman-Kalehoyuk in Anatolia, about 1800 BCE; the printing press: Gutenberg in Mainz about 1440; steam power:
     * Newcomen's engine, England 1712; electricity as a public supply: Holborn Viaduct in London and Pearl Street in New
     * York, 1882; computing: ENIAC, Philadelphia 1945).
     */
    private void registerDefaults() {
        register(new Technology("agriculture", "Agriculture", TechEra.ANCIENT, 1, java.util.List.of(),
                -9500, "the Fertile Crescent"));
        register(new Technology("writing", "Writing", TechEra.ANCIENT, 2, java.util.List.of("agriculture"),
                -3200, "Sumer and Egypt"));
        register(new Technology("steel", "Steel Working", TechEra.MEDIEVAL, 3, java.util.List.of("writing"),
                -1800, "Anatolia"));
        register(new Technology("printing_press", "Printing Press", TechEra.RENAISSANCE, 4, java.util.List.of("writing"),
                1440, "Mainz"));
        register(new Technology("steam_power", "Steam Power", TechEra.INDUSTRIAL, 6, java.util.List.of("steel"),
                1712, "England"));
        register(new Technology("electricity", "Electricity", TechEra.INDUSTRIAL, 7, java.util.List.of("steam_power"),
                1882, "London and New York"));
        register(new Technology("computing", "Computing", TechEra.MODERN, 9, java.util.List.of("electricity"),
                1945, "Philadelphia"));
    }
}
