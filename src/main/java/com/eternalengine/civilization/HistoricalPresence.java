package com.eternalengine.civilization;

import com.eternalengine.population.HydeGrid;
import com.eternalengine.terrabridge.OhmPresence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Answers "who would be here in this year?" from foundation civ data.
 *
 * Occupancy order:
 * <ol>
 *   <li>sourced or present-only gazetteer site occupied this year</li>
 *   <li>undated named place with estimated start (gravity / diffusion / parent box)</li>
 *   <li>dated OHM place from Terra++ disk cache</li>
 *   <li>estimated hinterland (gravity from a dated city/town — unnamed countryside)</li>
 *   <li>approximate empire core boxes</li>
 *   <li>territory polygons</li>
 *   <li>the ruler register: a Deccan ruler counts as presence; a world-record ruler (0.5-degree
 *       cell) only when the HYDE population model also has people on this tile</li>
 *   <li>estimated people from the HYDE population model, with or without a ruler: dense land
 *       everywhere, thinly settled land on a share of tiles ({@link HydeGrid})</li>
 * </ol>
 * Missing years are never treated as the beginning of time.
 */
public final class HistoricalPresence {

    /** The people in order, one per name (a display rule; see the call site). */
    private static List<FamousPeople.Person> distinctByName(List<FamousPeople.Person> people) {
        java.util.LinkedHashMap<String, FamousPeople.Person> by = new java.util.LinkedHashMap<>();
        for (FamousPeople.Person p : people) {
            by.putIfAbsent(p.name(), p);
        }
        return new java.util.ArrayList<>(by.values());
    }

    /**
     * A site's civHint read as a civilization id: the pack's own id when the register knows it, the
     * alias when exactly one polity answers to it, and the raw id otherwise.
     *
     * <p>Measured 2026-10-22: 96.4% of site hints were ids the register had never heard of, so this
     * branch - which decides the civilization of an NPC when no ruler is named - was naming pack ids.
     * {@link CivHints} maps the 49 unambiguous ones and refuses the rest, because "india" is not a
     * polity in any year and mapping it to one would be a lie told in a different direction.
     */
    private static String civIdOf(String hint) {
        return CivHints.resolve(hint, RulerRegister.get()).orElse(hint);
    }

    /** Hint only — not used to spawn. */
    public static final double POLITY_RADIUS_KM = 400.0d;

    private HistoricalPresence() {}

    public record Match(Civilization civ, double distanceKm, String reason) {}

    public record Result(
            int year,
            double lat,
            double lon,
            Optional<Match> occupant,
            Optional<HistoryGazetteer.Hit> site,
            Optional<OhmPresence.Hit> presentMap,
            Optional<OhmPresence.Hit> ohm,
            Optional<HistoryGazetteer.Hit> hinterland,
            Optional<HistoryGazetteer.Border> border,
            Optional<HistoryGazetteer.EventHit> event,
            Optional<RulerRegister.Hit> ruler,
            Optional<Match> nearestEver,
            Optional<HydeGrid.Reading> people,
            /**
             * The civilization the people standing here belong to — resolved <b>through the year</b>
             * (board 17). {@link #spawnCivId()} used to answer from the site's hint alone, which names
             * who FOUNDED the place: at Anyang in 430 BC that is still "China, Shang", a dynasty that
             * fell in 1046 BC, so the game stood 29 Shang townspeople in a town the Zhou held.
             */
            String spawnCiv
    ) {
        public boolean occupied() {
            return site.isPresent() || presentMap.isPresent() || ohm.isPresent()
                    || hinterland.isPresent() || border.isPresent() || occupant.isPresent()
                    || rulerHasPeople() || peopleHere();
        }

        /**
         * Is there a settlement standing here this year (a gazetteer site, a map footprint, a
         * border), as opposed to only the model's countryside people? A camp of hunters and
         * gatherers is occupied but not settled: it gets countryside people and countryside talk,
         * not a village with merchants (turn 230, the Secunderabad doubt).
         */
        public boolean settled() {
            return site.isPresent() || presentMap.isPresent() || ohm.isPresent()
                    || hinterland.isPresent() || border.isPresent();
        }

        /** The HYDE population model has people on this tile this year (estimated, not a record). */
        public boolean peopleHere() {
            return people.map(HydeGrid.Reading::present).orElse(false);
        }

        /**
         * A Deccan ruler counts as presence, as it always has. A world-record ruler (a 0.5-degree
         * cell) says who ruled the ground, not that anyone lived on it: that is the HYDE model's
         * answer. Without the HYDE grid a world ruler counts as before.
         */
        private boolean rulerHasPeople() {
            if (ruler.isEmpty()) {
                return false;
            }
            if (!RulerRegister.isGridPlace(ruler.get().place())) {
                return true;
            }
            return people.isEmpty() || people.get().present();
        }

        /**
         * <b>The civilization these people belong to, in the vocabulary of the civilization pack, and
         * only if it still stands in this year.</b>
         *
         * <p>Until board 17 this answered from the site's {@code civHint} directly. A hint says who
         * founded a place, not who holds it now: measured on a real save, one person in five stood in
         * a town owned by a civilization that had already fallen (Anyang's 29 Shang townspeople in
         * 430 BC; 28 Indus Valley people in 1200 BC, a century after the Indus fell). The answer is now
         * computed by {@link #spawnCivFor} where the year, the manager and the ruler register are all
         * in hand, and every candidate is checked with {@link Civilization#isActiveInYear}.
         */
        public String spawnCivId() {
            return spawnCiv == null || spawnCiv.isBlank() ? "neutral" : spawnCiv;
        }

        public SettlementRank rank() {
            if (site.isPresent()) {
                return site.get().site().rankAt(year);
            }
            if (presentMap.isPresent()) {
                return presentMap.get().rank();
            }
            if (ohm.isPresent()) {
                return ohm.get().rank();
            }
            if (hinterland.isPresent()) {
                return SettlementRank.VILLAGE;
            }
            // No site, no map, no town, no hinterland, no border: nothing says a settlement
            // stands here, so the honest answer is countryside (turn 231). The old
            // orElse(SettlementRank.VILLAGE) was the single lie behind the Secunderabad bug:
            // every reader of rank() was told a village stood in the stone age. When unsure,
            // claim the least.
            return border.map(HistoryGazetteer.Border::rank).orElse(SettlementRank.COUNTRYSIDE);
        }

        public String[] spawnRoles() {
            if (!settled()) {
                // Open country before any settlement stands here (turn 230): the people are what
                // the talk says they are - hunters, fishers and gatherers - and a camp has no
                // money-changer, no pedlar and no news-seller. Barter still reads as "anybody
                // swaps a little", which is exactly who is doing the swapping.
                return new String[] {"forager"};
            }
            String[] roles = site.map(h -> HistoryGazetteer.get().spawnRoles(h.site(), year))
                    .orElse(rank().roles());
            // Step 19b: where there are merchants and money, one money-changer stands with them.
            roles = com.eternalengine.money.MarketService.withMoneyChanger(roles, lat, lon, year);
            // Step 19g: where nobody trades, a trader in a town, a pedlar in a village with money.
            roles = com.eternalengine.money.MarketService.withTrader(roles, rank(), lat, lon, year);
            // Step 19d: and one person who brings the news: a paper seller, a newsletter writer or a town crier.
            return com.eternalengine.news.NewsEras.withNewsPerson(roles, rank(), ruler().isPresent(), lat, lon, year);
        }
    }

    /**
     * Everything recorded or estimated at this point in this year, in the active timeline. Game systems ask through
     * {@link com.eternalengine.core.WorldQuery#presence}, which adds the timeline and the universe (step 26, A1);
     * this is the layer below it.
     */
    public static Result query(CivilizationManager manager, double lat, double lon, int year) {
        return query(manager, lat, lon, year, HistoryGazetteer.suppressionFilter());
    }

    /** No history at all here (step 26: a universe other than the prime one has none recorded). */
    public static Result nobody(double lat, double lon, int year) {
        return new Result(year, lat, lon, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "neutral");
    }

    /** The same query for one timeline: {@code hiddenPins} are the events that timeline prevented. */
    public static Result query(CivilizationManager manager, double lat, double lon, int year,
                               java.util.function.Predicate<String> hiddenPins) {
        HistoryGazetteer gaz = HistoryGazetteer.get();
        Optional<HistoryGazetteer.Hit> site = gaz.nearestOccupied(lat, lon, year);
        Optional<OhmPresence.Hit> presentMap = site.isPresent()
                ? Optional.empty()
                : OhmPresence.nearestPresent(lat, lon, year);
        Optional<OhmPresence.Hit> ohm = site.isPresent() || presentMap.isPresent()
                ? Optional.empty()
                : OhmPresence.nearestDated(lat, lon, year);
        Optional<HistoryGazetteer.Hit> hinterland = site.isPresent() || presentMap.isPresent() || ohm.isPresent()
                ? Optional.empty()
                : gaz.estimatedHinterland(lat, lon, year);
        Optional<HistoryGazetteer.Border> border = gaz.borderAt(lat, lon, year);
        Optional<HistoryGazetteer.EventHit> event = gaz.nearestEvent(lat, lon, year, hiddenPins);
        Optional<RulerRegister.Hit> ruler = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year);
        Optional<HydeGrid.Reading> people = HydeGrid.get().reading(lat, lon, year);
        if (manager == null) {
            return new Result(year, lat, lon, Optional.empty(), site, presentMap, ohm, hinterland, border, event, ruler,
                    Optional.empty(), people, "neutral");
        }

        Optional<Match> fromTerritory = occupantFromTerritory(manager, lat, lon, year);
        Optional<Match> fromSiteCiv = site.flatMap(h -> manager.getCivilization(h.site().civHint())
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> new Match(civ, h.distanceKm(), "site " + h.site().id())));
        Optional<Match> fromPresent = presentMap.flatMap(p -> border.flatMap(b -> manager.getCivilization(b.civId())
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> new Match(civ, p.distanceKm(), "present map " + p.name()))));
        Optional<Match> fromHinterland = hinterland.flatMap(h -> manager.getCivilization(h.site().civHint())
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> new Match(civ, h.distanceKm(), "estimated hinterland of " + h.site().id())));
        Optional<Match> fromBorder = border.flatMap(b -> manager.getCivilization(b.civId())
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> new Match(civ, 0.0d, "border " + b.id())));
        Optional<Match> nearestEver = nearestCapital(manager, lat, lon, year, false);

        Optional<Match> occupant = fromSiteCiv
                .or(() -> fromPresent)
                .or(() -> fromHinterland)
                .or(() -> fromBorder)
                .or(() -> fromTerritory);
        String spawnCiv = spawnCivFor(manager, lat, lon, year, occupant, site, hinterland, presentMap, ruler, border);
        return new Result(year, lat, lon, occupant, site, presentMap, ohm, hinterland, border, event, ruler, nearestEver,
                people, spawnCiv);
    }

    /** Step 26 (A5): the recorded/estimated mark of a ruler answer, for the operator's {@code /ee who}. */
    static String rulerMark(RulerRegister.Hit r, int year) {
        if (!r.estimated()) {
            return " [recorded]";
        }
        return year > RulerRegister.get().recordedUntil()
                ? " [estimate: the record ends in " + RulerRegister.get().recordedUntil() + "; its states carried forward]"
                : " [estimate: taken from the neighbouring squares]";
    }

    public static String describe(Result result) {
        String header = String.format(java.util.Locale.ROOT,
                "Year %d at lat=%.4f lon=%.4f (named site; present map; dated OHM; estimated hinterland; box; ruler; estimated people)",
                result.year(), result.lat(), result.lon());
        StringBuilder sb = new StringBuilder(header);
        result.site().ifPresent(h -> {
            sb.append("\n  site: ").append(h.site().packId())
                    .append("/").append(h.site().id())
                    .append(" \"").append(h.site().displayName()).append("\"")
                    .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", h.distanceKm()))
                    .append(" km occupied ").append(h.site().occupiedStart())
                    .append("..").append(h.site().occupiedEnd())
                    .append(" rank=").append(h.site().rankAt(result.year()).name().toLowerCase(java.util.Locale.ROOT))
                    .append(" civHint=").append(h.site().civHint())
                    .append(" confidence=").append(h.site().confidence());
            if (h.site().presentOnly() || "estimated".equalsIgnoreCase(h.site().confidence())) {
                sb.append(" (estimated year: gravity/diffusion/parent, not a sourced founding)");
            }
            if (!h.site().partOf().isEmpty()) {
                sb.append(" partOf=").append(String.join("/", h.site().partOf()));
            }
        });
        result.ruler().ifPresent(r -> sb.append("\n  ruler: ").append(r.ruler())
                .append(" (").append(r.civHint()).append(") ")
                .append(r.start()).append("..").append(r.end())
                .append(" at ").append(r.place())
                .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", r.distanceKm()))
                .append(" km").append(rulerMark(r, result.year())));
        result.ruler().ifPresent(r -> LeaderRegister.get().facts(r.wikidata()).ifPresent(f -> {
            f.headIn(result.year()).ifPresent(h -> sb.append("\n  head of state: ").append(h.display())
                    .append(" ").append(h.start()).append("..").append(h.end())
                    .append(h.estimated() ? " [estimate: the list gives no end; start + 25 years]" : ""));
            f.governmentIn(result.year()).ifPresent(g -> sb.append("\n  head of government: ").append(g.display())
                    .append(" ").append(g.start()).append("..").append(g.end()));
            if (!f.languages().isEmpty() || !f.religions().isEmpty() || !f.capital().isBlank()) {
                sb.append("\n  state facts (Wikipedia: ").append(f.article()).append("):");
                if (!f.languages().isEmpty()) {
                    sb.append(" languages ").append(String.join(", ", f.languages())).append(";");
                }
                if (!f.religions().isEmpty()) {
                    sb.append(" religions ").append(String.join(", ", f.religions())).append(";");
                }
                if (!f.capital().isBlank()) {
                    sb.append(" capital ").append(f.capital());
                }
            }
        }));
        {
            String qid = result.ruler().map(RulerRegister.Hit::wikidata).orElse(null);
            List<FamousPeople.Person> living = FamousPeople.get().livingIn(qid, result.lat(), result.lon(), result.year());
            List<FamousPeople.Person> died = FamousPeople.get().diedIn(qid, result.lat(), result.lon(), result.year());
            // One line must not say the same person twice. The master carries a handful of true
            // duplicates - the same life from two source builds (measured 2026-10-22: 38 pairs, e.g.
            // Nikos Skalkottas born 1901 in one row and 1904 in the other) - and with the land
            // fallback both rows now cover the same point. Deduplicating here is presentational: no
            // row is deleted, so nothing that reads the master by row number can be unsettled.
            List<FamousPeople.Person> livingShown = distinctByName(living);
            if (!livingShown.isEmpty()) {
                sb.append("\n  famous people of this land alive now: ").append(livingShown.size()).append(", e.g. ")
                        .append(String.join("; ", livingShown.stream().limit(3).map(FamousPeople.Person::display).toList()));
            }
            if (!died.isEmpty()) {
                sb.append("\n  died this year: ")
                        .append(String.join("; ", died.stream().limit(3).map(FamousPeople.Person::display).toList()));
            }
        }
        String life = EventNews.wayOfLifeLine(result);
        if (!life.isEmpty()) {
            sb.append("\n  way of life (").append(EventNews.wayOfLifeSource(result)).append("): ").append(life);
        }
        LocalLanguages.get().peoplesLanguage(result.lat(), result.lon(), result.year()).ifPresent(h ->
                sb.append("\n  language of the people around here (Glottolog; where it is spoken today): ")
                        .append(h.language().name())
                        .append(String.format(java.util.Locale.ROOT, " (%.0f km)", h.distanceKm())));
        result.ruler().flatMap(r -> LeaderRegister.get().facts(r.wikidata()))
                .flatMap(f -> f.religionSharesIn(result.year()))
                .ifPresent(ry -> {
                    List<String> parts = new java.util.ArrayList<>();
                    for (LeaderRegister.Share sh : ry.shares()) {
                        parts.add(String.format(java.util.Locale.ROOT, "%s %.0f%%", sh.faith(), sh.percent()));
                    }
                    sb.append("\n  faiths (Correlates of War World Religion Data, ").append(ry.year()).append("): ")
                            .append(String.join(", ", parts));
                });
        {
            List<FamousPeople.Person> born = FamousPeople.get().bornNear(result.lat(), result.lon(), result.year(), 15.0d);
            if (!born.isEmpty()) {
                List<String> names = new java.util.ArrayList<>();
                for (FamousPeople.Person p : born.subList(0, Math.min(3, born.size()))) {
                    names.add(p.display() + (p.birth() != FamousPeople.UNKNOWN ? " (born " + p.birth() + ")" : ""));
                }
                sb.append("\n  born near here: ").append(born.size()).append(", e.g. ").append(String.join("; ", names));
            }
        }
        String trade = EventNews.tradeLine(result);
        if (!trade.isEmpty()) {
            sb.append("\n  trade route (towns of Wikipedia's trade-route articles): ").append(trade);
        }
        String road = EventNews.roadLine(result);
        if (!road.isEmpty()) {
            sb.append("\n  road (Itiner-e, Roman roads): ").append(road);
        }
        result.people().ifPresent(p -> {
            if (p.present()) {
                sb.append("\n  estimated people: ").append(p.display())
                        .append(p.share() < 0.995d
                                ? String.format(java.util.Locale.ROOT, "; thinly settled, people on about %.0f%% of the land",
                                        p.share() * 100.0d)
                                : "")
                        .append(" (unnamed countryside; a population model, not a record)");
            } else if (result.ruler().isPresent() && RulerRegister.isGridPlace(result.ruler().get().place())) {
                sb.append("\n  no one on this spot this year by the population model: ").append(p.display())
                        .append(p.share() > 0.0d
                                ? String.format(java.util.Locale.ROOT, "; people on about %.0f%% of the land around here",
                                        p.share() * 100.0d)
                                : "; nobody lives on this ground");
            }
        });
        result.site().ifPresent(h -> {
            var traits = HistoryGazetteer.get().traitsFor(h.site().id(), result.year());
            if (!traits.isEmpty()) {
                sb.append("\n  traits: ");
                sb.append(traits.stream().map(HistoryGazetteer.Trait::id)
                        .reduce((a, b) -> a + "," + b).orElse(""));
            }
        });
        result.presentMap().ifPresent(o -> sb.append("\n  present map: ").append(o.name())
                .append(" place=").append(o.place())
                .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", o.distanceKm()))
                .append(" km estimatedStart=").append(o.startYear())
                .append(" rank=").append(o.rank().name().toLowerCase(java.util.Locale.ROOT))
                .append(" (undated map name; year estimated, not a sourced founding)"));
        result.ohm().ifPresent(o -> sb.append("\n  ohm: ").append(o.name())
                .append(" place=").append(o.place())
                .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", o.distanceKm()))
                .append(" km ").append(o.startYear()).append("..")
                .append(o.endYear() == Integer.MIN_VALUE ? "open" : Integer.toString(o.endYear()))
                .append(" rank=").append(o.rank().name().toLowerCase(java.util.Locale.ROOT))
                .append(" (dated OHM cache only)"));
        result.hinterland().ifPresent(h -> sb.append("\n  estimated hinterland: village of ")
                .append(h.site().id())
                .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", h.distanceKm()))
                .append(" km (gravity/diffusion; unnamed countryside, not this hamlet's founding year)"));
        result.border().ifPresent(b -> sb.append("\n  border: ").append(b.id())
                .append(" civ=").append(b.civId())
                .append(" ").append(b.start()).append("..").append(b.end())
                .append(" rank=").append(b.rank().name().toLowerCase(java.util.Locale.ROOT))
                .append(" (approx core, not a surveyed frontier)"));
        result.event().ifPresent(eh -> sb.append("\n  event: ").append(eh.event().id())
                .append(" \"").append(eh.event().name()).append("\"")
                .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", eh.distanceKm()))
                .append(" km ").append(eh.event().start()).append("..").append(eh.event().end())
                .append(" kind=").append(eh.event().kind())
                .append(" effect=").append(eh.event().effect())
                .append(HistoryGazetteer.isRegional(eh.event())
                        ? " (regional news: a whole region or country; cannot be stopped here)"
                        : " (short overlay; not a town; not attacker spawn)"));
        if (result.occupant().isPresent()) {
            Match m = result.occupant().get();
            sb.append("\n  occupant: ").append(m.civ().id())
                    .append(" \"").append(m.civ().getCurrentName(result.year())).append("\"")
                    .append(" ").append(String.format(java.util.Locale.ROOT, "%.0f", m.distanceKm()))
                    .append(" km (").append(m.reason()).append(")");
            // Two layers can name different polities for the same ground and the same year. Saying
            // so beats silently picking one: a blanket site hint ("everything here is India today")
            // and a tight authored box ("this ground was Hyderabad State, 1724..1948") are different
            // kinds of evidence, and the reader is entitled to see both before trusting either.
            if (result.border().isPresent()
                    && !m.civ().id().equalsIgnoreCase(result.border().get().civId())) {
                HistoryGazetteer.Border b = result.border().get();
                sb.append("\n  overlapping claim: ").append(b.civId())
                        .append(" ").append(b.start()).append("..").append(b.end())
                        .append(" (box covers this point too; ").append(m.civ().id())
                        .append(" chosen via ").append(m.reason()).append(")");
            }
            return sb.toString();
        }
        if (result.site().isPresent() || result.presentMap().isPresent() || result.ohm().isPresent()
                || result.hinterland().isPresent() || result.border().isPresent() || result.occupied()) {
            return sb.toString();
        }
        if (result.event().isPresent()) {
            sb.append("\n  occupant: none — event overlay only; not a town; no NPC from this event");
            return sb.toString();
        }
        sb.append("\n  occupant: none — no sourced site here this year");
        result.nearestEver().ifPresent(m -> {
            Civilization c = m.civ();
            String when;
            if (result.year() < c.foundingYear()) {
                when = "not founded until " + c.foundingYear();
            } else if (result.year() >= c.fallYear()) {
                when = "fell " + c.fallYear();
            } else {
                when = "active but " + String.format(java.util.Locale.ROOT, "%.0f", m.distanceKm())
                        + " km away (outside radius)";
            }
            sb.append("\n  nearest capital: ").append(c.id())
                    .append(" \"").append(c.name()).append("\" ")
                    .append(String.format(java.util.Locale.ROOT, "%.0f", m.distanceKm()))
                    .append(" km (").append(when).append(")");
        });
        return sb.toString();
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        return 2.0d * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    private static Optional<Match> occupantFromTerritory(
            CivilizationManager manager, double lat, double lon, int year) {
        return manager.territoryManager().getOwner(lat, lon, year)
                .flatMap(manager::getCivilization)
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> new Match(civ,
                        haversineKm(lat, lon, civ.capitalLat(), civ.capitalLon()),
                        "territory polygon"));
    }

    /** A civ-pack id, but only if that civilization exists and still stands in {@code year}. */
    private static String aliveCivId(CivilizationManager manager, String id, int year) {
        if (manager == null || id == null || id.isBlank()) {
            return null;
        }
        return manager.getCivilization(id)
                .filter(civ -> civ.isActiveInYear(year))
                .map(civ -> civ.id())
                .orElse(null);
    }

    /**
     * A pack hint, read through {@link CivHints} first and then as a civ id, and only if it stands this
     * year. {@code india} and {@code rome} name a place over time and resolve to nothing by design;
     * that is the honest answer, and the caller falls through to the year-aware layers.
     */
    private static String aliveHint(CivilizationManager manager, String hint, int year) {
        String direct = aliveCivId(manager, hint, year);
        if (direct != null) {
            return direct;
        }
        return CivHints.resolve(hint, RulerRegister.get())
                .map(target -> aliveCivId(manager, target, year))
                .orElse(null);
    }

    /**
     * How far the nearest living civilization may be before a recorded ruler here no longer implies
     * that its people are these people. 1,000 km: wide enough for a steppe empire whose capital is far
     * from its frontier, narrow enough that an empty sea or desert is not filled with somebody's
     * townsfolk.
     */
    private static final double RULER_PEOPLE_KM = 1000.0d;

    /**
     * <b>Who stands here, in this year</b> — the one place the civilization of a place's people is
     * decided. Every answer is a civilization that is alive in {@code year}; when the record names
     * none, the answer is {@code neutral} and the game says so rather than peopling the place with a
     * civilization that has fallen.
     *
     * <p>The order, and why:
     * <ol>
     *   <li><b>the occupant</b> — already filtered to civilizations alive this year;</li>
     *   <li><b>the site's own hint</b> — <i>if</i> that civilization still stands. This is the fix:
     *       Anyang's hint is Shang, which is the right answer in 1200 BC and wrong in 430 BC;</li>
     *   <li><b>the estimated hinterland's hint</b> — same test;</li>
     *   <li><b>the recorded ruler</b> — the ruler register is asked by year already. Its polity
     *       spelling rarely matches a civ-pack id (it names 1,581 polities; the pack has 133
     *       civilizations), so a miss falls through;</li>
     *   <li><b>the nearest civilization alive this year</b>, but only when a ruler <i>is</i> recorded
     *       here: somebody is known to have held this ground, so the people take the nearest living
     *       civilization of that year — at Anyang in 430 BC, Zhou;</li>
     *   <li><b>the border</b> (its own years), then the map layers, then {@code neutral}.</li>
     * </ol>
     */
    private static String spawnCivFor(CivilizationManager manager, double lat, double lon, int year,
                                      Optional<Match> occupant,
                                      Optional<HistoryGazetteer.Hit> site,
                                      Optional<HistoryGazetteer.Hit> hinterland,
                                      Optional<OhmPresence.Hit> presentMap,
                                      Optional<RulerRegister.Hit> ruler,
                                      Optional<HistoryGazetteer.Border> border) {
        if (occupant.isPresent()) {
            return occupant.get().civ().id();
        }
        if (site.isPresent()) {
            String id = aliveHint(manager, site.get().site().civHint(), year);
            if (id != null) {
                return id;
            }
        }
        if (hinterland.isPresent()) {
            String id = aliveHint(manager, hinterland.get().site().civHint(), year);
            if (id != null) {
                return id;
            }
        }
        if (ruler.isPresent()) {
            String id = aliveHint(manager, ruler.get().civHint(), year);
            if (id != null) {
                return id;
            }
            // A ruler is recorded here, so the ground was held by somebody: the people are the nearest
            // civilization that is alive in this year. Without a recorded ruler nothing is assumed.
            Optional<Match> near = nearestCapital(manager, lat, lon, year, true);
            if (near.isPresent() && near.get().distanceKm() <= RULER_PEOPLE_KM) {
                return near.get().civ().id();
            }
        }
        if (border.isPresent()) {
            String id = aliveCivId(manager, border.get().civId(), year);
            if (id != null) {
                return id;
            }
        }
        if (presentMap.isPresent()) {
            return border.map(HistoryGazetteer.Border::civId).orElse("neutral");
        }
        return "neutral";
    }

    private static Optional<Match> nearestCapital(
            CivilizationManager manager, double lat, double lon, int year, boolean activeOnly) {
        return manager.ids().stream()
                .map(manager::getCivilization)
                .flatMap(Optional::stream)
                .filter(civ -> !activeOnly || civ.isActiveInYear(year))
                .map(civ -> new Match(civ,
                        haversineKm(lat, lon, civ.capitalLat(), civ.capitalLon()),
                        activeOnly ? "active capital" : "nearest capital ever"))
                .min(Comparator.comparingDouble(Match::distanceKm));
    }
}
