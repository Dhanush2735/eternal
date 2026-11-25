import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.HistoryGazetteer.EventHit;

import java.util.*;

/**
 * Simulates /tpll <random land coord> + /terra timejump <year> and reports how often
 * the player would land within sight of a history event, using the mod's own
 * HistoryGazetteer.eventsNear query.
 */
public class DensityProbe {
    // Coarse land mask: lat bands x lon bands known to contain land.
    static boolean isLand(double lat, double lon) {
        if (lat > 83 || lat < -58) return false;
        // very rough continent boxes, good enough for a density estimate
        if (lat > 35 && lat < 72 && lon > -10 && lon < 60) return true;    // Europe/W Asia
        if (lat > 18 && lat < 54 && lon > 60 && lon < 145) return true;    // C+E Asia
        if (lat > -11 && lat < 35 && lon > -18 && lon < 52) return true;   // Africa
        if (lat > 5 && lat < 38 && lon > 66 && lon < 98) return true;      // India
        if (lat > 24 && lat < 50 && lon > -125 && lon < -66) return true;  // USA
        if (lat > -56 && lat < 13 && lon > -82 && lon < -34) return true;  // S America
        if (lat > -40 && lat < -10 && lon > 112 && lon < 154) return true; // Australia
        if (lat > 48 && lat < 72 && lon > -141 && lon < -52) return true;  // Canada
        return false;
    }

    public static void main(String[] args) {
        HistoryGazetteer g = HistoryGazetteer.get();
        System.out.println("events loaded: " + g.eventCount());

        int[] years = {-2500, -1500, -500, 1, 500, 1000, 1300, 1500, 1700, 1850, 1900, 1944, 2000};
        double[] radii = {25.0, 50.0, 100.0};
        Random rnd = new Random(42);
        int N = 400;

        // pre-sample land points once so every year is measured on the same sample
        double[] lat = new double[N], lon = new double[N];
        int n = 0;
        while (n < N) {
            double la = -58 + rnd.nextDouble() * 141;
            double lo = -180 + rnd.nextDouble() * 360;
            if (isLand(la, lo)) { lat[n] = la; lon[n] = lo; n++; }
        }

        System.out.println("\n% of " + N + " random land points that have an event within R km, by year");
        System.out.printf("%8s %10s %10s %10s%n", "year", "R=25km", "R=50km", "R=100km");
        for (int y : years) {
            StringBuilder sb = new StringBuilder();
            for (double r : radii) {
                int hit = 0;
                for (int i = 0; i < N; i++) {
                    if (!g.eventsNear(lat[i], lon[i], y, r).isEmpty()) hit++;
                }
                sb.append(String.format("%9.1f%%", 100.0 * hit / N));
            }
            System.out.printf("%8d%s%n", y, sb);
        }

        // How dense is it at the best-covered year vs the ancient ones?
        System.out.println("\nevents alive globally per year (activeIn):");
        for (int y : years) {
            int alive = 0;
            for (var e : g.allEvents()) if (e.activeIn(y)) alive++;
            System.out.printf("  %6d  %6d alive   %s%n", y, alive,
                    "#".repeat(Math.min(60, alive / 60)));
        }
    }
}
