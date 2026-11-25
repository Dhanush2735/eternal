package com.eternalengine.here;

import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.RulerRegister;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.TownFacts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Step 62, "history at your feet": one reading of where you stand and what year it is.
 *
 * <p>The registers already held every part of this reading (the gazetteer's sites and events, the towns' names by
 * era and people counts, the ruler register's spans); what was missing was the door a player can open. This class
 * is that door's page: it asks the registers in a fixed order and prints plain lines. It adds no fact of its own.
 *
 * <p><b>Where every line comes from.</b> The place line is the gazetteer (a recorded site, else an estimated
 * countryside village, else nobody - and "nobody" is printed, never a guess). The town lines are {@link TownFacts},
 * worded by that register ("About ... people", "People say the town was founded ..."). The ruler line is
 * {@link RulerRegister}, and when the register marks its answer as an estimate (years past its source, or a square
 * covered from its neighbours) the line says so. Event lines
 * name the event and its distance. Two registers that disagree are printed beside each other, never merged.
 *
 * <p><b>The limits, printed as they are met:</b> nothing here moves a villager or builds a block (that is the
 * wired systems' job, step 62's later parts); the reading is only as wide as the packs; beyond the last recorded
 * year the lines thin out to "the register names no ruler" and "Nobody the data records", which is the AD 3000
 * guardrail doing its work rather than an empty bug.
 */
public final class HereAndNow {

    /** How far an event still counts as "hereabouts", in km. Beyond this the reading stays silent about it. */
    public static final double EVENT_KM = 200.0d;
    /** This close the place is where you stand; further away the line names the distance. */
    public static final double STANDING_KM = 15.0d;
    /** At most this many event lines, so the reading stays a page and not a dump. */
    public static final int MAX_EVENTS = 3;

    /** One reading: the year asked, the lines in fixed order, and the one-line form for quiet moments. */
    public record Reading(int year, List<String> lines, String shortLine) {

        public String text() {
            return String.join("\n", lines);
        }
    }

    private HereAndNow() {
    }

    /** "2500 BC", "AD 100", "1500" - the year as the reading says it. */
    public static String yearText(int year) {
        if (year < 0) {
            return (-year) + " BC";
        }
        return year < 1000 ? "AD " + year : Integer.toString(year);
    }

    /** The full page for one point and year. Same inputs, same page, always. */
    public static Reading read(double lat, double lon, int year) {
        List<String> lines = new ArrayList<>();
        String when = yearText(year);
        lines.add("It is " + when + ".");

        HistoryGazetteer gaz = HistoryGazetteer.get();
        TownFacts towns = TownFacts.get();
        RulerRegister rulers = RulerRegister.get();

        // ---------------------------------------------------------------- the place you stand in
        // The presence layer the spawner also reads (HistoricalPresence): beyond the named places,
        // the population grid counts a thin presence on settled land. The reading must agree with
        // the people standing in front of you, so the grid is asked here too (turn 221, doubt 1).
        boolean gridSays = com.eternalengine.population.HydeGrid.get()
                .reading(lat, lon, year).map(com.eternalengine.population.HydeGrid.Reading::present)
                .orElse(false);

        String shownName = null;
        String shortPlace = null;
        Optional<HistoryGazetteer.Hit> site = gaz.nearestOccupied(lat, lon, year);
        Optional<HistoryGazetteer.Hit> country = site.isPresent() ? Optional.empty()
                : gaz.estimatedHinterland(lat, lon, year);
        if (site.isPresent()) {
            HistoryGazetteer.Hit hit = site.get();
            shownName = tidyName(hit.site().displayName());
            SettlementRank rank = hit.site().rankAt(year);
            String kind = rank == SettlementRank.CITY ? "city" : rank == SettlementRank.TOWN ? "town" : "village";
            if (hit.distanceKm() <= STANDING_KM) {
                lines.add("You stand at " + shownName + ", a " + kind + " of this year.");
            } else {
                lines.add("You stand " + km(hit.distanceKm()) + " from " + shownName
                        + ", the nearest place the data records for this year, a " + kind + ".");
            }
        } else if (country.isPresent()) {
            HistoryGazetteer.Hit hit = country.get();
            shownName = tidyName(hit.site().displayName());
            lines.add("You stand in the countryside of " + shownName
                    + ", in one of the estimated villages of this year (the record names no village here).");
        } else if (gridSays) {
            lines.add("Nobody the record names lives near here in " + when
                    + ", but the population grid (HYDE 3.2.1) counts a thin presence on this land:"
                    + " a modelled presence, not a measurement.");
            shortPlace = "Here, where only the population grid counts a few people";
        } else {
            lines.add("Nobody the data records lives near here in " + when + ".");
            shortPlace = "Here, where nobody the data records lives";
        }

        // ---------------------------------------------------------------- the town's own lines
        Optional<TownFacts.Town> town = towns.near(lat, lon, year);
        String rulerName = null;
        Optional<RulerRegister.Hit> ruler = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year);
        if (ruler.isPresent()) {
            rulerName = ruler.get().ruler();
        }
        if (town.isPresent()) {
            TownFacts.Town t = town.get();
            String townName = tidyName(t.nameIn(year));
            if (shownName == null) {
                shownName = townName;
            }
            add(lines, t.formerNameLine(year));
            add(lines, t.alsoLine(year));
            add(lines, t.peopleLine(year, townName));
            add(lines, t.growthLine(year));
            add(lines, t.foundedLine(year));
            add(lines, t.provinceLine(year, rulerName));
            add(lines, t.headLine(year));
        }

        // ---------------------------------------------------------------- who rules here
        if (ruler.isPresent()) {
            RulerRegister.Hit hit = ruler.get();
            StringBuilder line = new StringBuilder("The ruler the data names here is ")
                    .append(hit.ruler()).append(" (").append(yearText(hit.start()))
                    .append(" to ").append(yearText(hit.end())).append(")");
            if (hit.estimated()) {
                line.append(", a span the register marks as an estimate");
            }
            if (hit.distanceKm() > STANDING_KM) {
                line.append("; the register answers for a place ").append(km(hit.distanceKm())).append(" away");
            }
            lines.add(line.append(".").toString());
        } else {
            lines.add("The register names no ruler here in " + when + ".");
        }

        // ---------------------------------------------------------------- what the record of these years names here
        List<HistoryGazetteer.EventHit> events = new ArrayList<>(gaz.eventsNear(lat, lon, year, EVENT_KM));
        events.sort(Comparator.comparingDouble(HistoryGazetteer.EventHit::distanceKm)
                .thenComparing(e -> e.event().name() == null ? "" : e.event().name()));
        int shown = 0;
        for (HistoryGazetteer.EventHit e : events) {
            if (shown >= MAX_EVENTS) {
                break;
            }
            lines.add((shown == 0 ? "Near here the record names " : "Also ")
                    + e.event().name() + " (" + km(e.distanceKm()) + " away).");
            shown++;
        }
        if (events.size() > shown) {
            lines.add("The record names " + (events.size() - shown) + " more hereabouts in " + when + ".");
        }

        // ---------------------------------------------------------------- whose land the border data counts it
        gaz.borderAt(lat, lon, year).ifPresent(b ->
                lines.add("The borders data counts this land as " + b.civId() + "."));

        return new Reading(year, List.copyOf(lines), shortLine(year, when,
                shortPlace != null ? shortPlace : shownName, ruler));
    }

    /** The one-line form: the year, the place, who rules. Printed when the year changes, so the world answers at once. */
    public static String shortLine(int year, String when, String placeClause, Optional<RulerRegister.Hit> ruler) {
        String place = placeClause == null ? "Here, where nobody the data records lives" : placeClause;
        String under;
        if (ruler.isPresent()) {
            under = "under " + ruler.get().ruler();
            if (ruler.get().estimated()) {
                under += " (an estimate)";
            }
        } else {
            under = "with no ruler the register names";
        }
        return "It is " + when + ". " + place + ", " + under + ".";
    }

    /** "golconda" reads "Golconda", "mohenjo_daro" reads "Mohenjo Daro": presentation only, never new data. */
    private static String tidyName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String s = name.trim().replace('_', ' ').replaceAll("\\s+", " ");
        boolean allLower = s.equals(s.toLowerCase(java.util.Locale.ROOT));
        if (!allLower) {
            return s;
        }
        String[] small = {"of", "the", "and", "de", "da", "del", "di", "van", "von", "el", "al", "bin", "ibn", "la", "le"};
        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            String w = words[i];
            boolean keep = i > 0 && java.util.Arrays.asList(small).contains(w);
            if (keep || w.isEmpty()) {
                out.append(w);
            } else {
                out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return out.toString();
    }

    private static void add(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    /** Whole kilometres, one decimal below ten, so "3 km" and "6.8 km" and never "3.14159 km". */
    private static String km(double distanceKm) {
        return distanceKm < 10.0d
                ? String.format(java.util.Locale.ROOT, "%.1f km", distanceKm)
                : Math.round(distanceKm) + " km";
    }
}
