import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.HistoryGazetteer.Event;
import com.eternalengine.civilization.HistoryGazetteer.EventHit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs the mod's real HistoryGazetteer load path against the installed data,
 * then queries it exactly the way /tpll + /terra timejump would.
 */
public class LoadCheck {
    public static void main(String[] args) {
        HistoryGazetteer g = HistoryGazetteer.get();   // triggers ensureLoaded()

        System.out.println("=== gazetteer loaded ===");
        System.out.println("  sites   : " + g.siteCount());
        System.out.println("  events  : " + g.eventCount());
        System.out.println("  borders : " + g.borderCount());

        Map<String, Integer> byPack = g.allEvents().stream()
                .collect(Collectors.groupingBy(Event::packId, Collectors.summingInt(e -> 1)));
        System.out.println("\n=== events per pack (as the mod sees them) ===");
        byPack.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-26s %6d%n", e.getKey(), e.getValue()));

        boolean wiki = byPack.containsKey("wikipedia_year_pages");
        System.out.println("\n  wikipedia_year_pages pack LOADED: " + wiki
                + "  (" + byPack.getOrDefault("wikipedia_year_pages", 0) + " events)");

        System.out.println("\n=== /tpll + /terra timejump probes (eventsNear, 150 km) ===");
        probe(g, "Hyderabad",       17.3850,  78.4867, 1687);
        probe(g, "Mohenjo-daro",    27.3250,  68.1370, -2450);
        probe(g, "Megiddo",         32.5860,  35.1830, -1457);
        probe(g, "Rome",            41.9028,  12.4964,  1600);
        probe(g, "London",          51.5074,  -0.1278, 1066);
        probe(g, "Tenochtitlan",    19.4326, -99.1332, 1519);
        probe(g, "Timbuktu",        16.7735,  -3.0074, 1450);
        probe(g, "Kyoto",           35.0116, 135.7681,  1600);

        System.out.println("\n=== year sweep: events alive anywhere on Earth, radius 500 km of Hyderabad ===");
        for (int y = -2900; y <= 2024; y += 400) {
            System.out.printf("  %6d  %d%n", y, g.eventsNear(17.3850, 78.4867, y, 500).size());
        }
    }

    private static void probe(HistoryGazetteer g, String label, double lat, double lon, int year) {
        List<EventHit> hits = g.eventsNear(lat, lon, year, 150.0);
        System.out.printf("%n  %s  (%.2f, %.2f)  year %d  ->  %d event(s)%n",
                label, lat, lon, year, hits.size());
        hits.stream().sorted(Comparator.comparingDouble(EventHit::distanceKm)).limit(4)
                .forEach(h -> {
                    Event e = h.event();
                    System.out.printf("      %6d  %-11s %6.1f km  [%s]  %s%n",
                            e.start(), e.kind(), h.distanceKm(), e.packId(),
                            e.name().length() > 62 ? e.name().substring(0, 62) + "..." : e.name());
                });
    }
}
