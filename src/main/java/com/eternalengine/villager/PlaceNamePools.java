package com.eternalengine.villager;

import com.eternalengine.civilization.RulerRegister;

/**
 * A name pool for a civilization the keyword tables do not recognise, chosen from <b>where the
 * polity actually ruled</b> rather than from how its id happens to be spelled.
 *
 * <h3>Why this exists (2026-10-24, requirement 2, step R2-d)</h3>
 * {@link VillagerNameDatabase#cultureFor} reads the civ id as a string and matches keywords. That
 * works for the ids somebody thought of and fails silently for the rest: measured on the shipped
 * register, <b>818 of 1,567 polities (52.2%) fell through to the generic pool</b> - a town in
 * Ahmadnagar or Achaea was peopled by "Villager Alex Stone". The keyword list cannot be completed
 * by hand: the register holds ~1,500 polities and every data update adds more.
 *
 * <p>The ruler register already knows the one thing a keyword can only guess at: the recorded
 * extent of each polity, including the centre of the cells it ruled ({@code RulerRegister.Extent}).
 * This class turns that centre into one of the eleven pools the game already has. It invents no
 * history: the pool is chosen by latitude and longitude, the same rule a person standing there
 * would use to say what the place sounds like.
 *
 * <h3>What it is honest about</h3>
 * <ul>
 *   <li><b>The pool is coarse by design.</b> Eleven pools cover the world; a polity gets the pool of
 *       its own region, not a table for its own dynasty. The existing class documents the same
 *       choice: "a civ getting the right region's names is worth far more than a pool per dynasty".</li>
 *   <li><b>It never overrides a keyword.</b> It is the last step before the generic pool, so every
 *       id that is already named correctly keeps exactly the names it had.</li>
 *   <li><b>A polity with no recorded extent keeps the generic pool.</b> An imperial centre that
 *       crosses two worlds gets the box its centre falls in; that is a known limit, not a claim.</li>
 * </ul>
 */
public final class PlaceNamePools {

    private PlaceNamePools() {
    }

    /** One box: a pool, and the latitudes and longitudes it covers. Most specific first. */
    private record Box(String pool, double latMin, double latMax, double lonMin, double lonMax) {
        boolean holds(double lat, double lon) {
            return lat >= latMin && lat <= latMax && lon >= lonMin && lon <= lonMax;
        }
    }

    /*
     * The boxes, ordered most specific first, so Mesopotamia is not swallowed by the Persianate
     * world and the Deccan is not swallowed by India. The values are the regions the pools were
     * written for, not a claim about any border in any year:
     *
     *   deccan        the plateau - Telangana, Maharashtra, Karnataka, Andhra
     *   indic         the rest of South Asia, and Lanka
     *   mesopotamian  the Tigris-Euphrates country
     *   southeast_asian  Thailand, Cambodia, Vietnam, the Malay world, Indonesia
     *   east_asian    China, Korea, Japan, Vietnam, Mongolia
     *   north_african the Maghreb, Egypt and the Nile
     *   persianate    Iran, Central Asia, Anatolia, the Levant, Arabia, the Caucasus
     *   african       sub-Saharan Africa
     *   americas      the Americas and the Pacific
     *   mediterranean Europe and the Mediterranean coasts
     */
    private static final Box[] BOXES = {
            new Box("deccan", 7.5, 23.5, 72.5, 85.5),
            new Box("indic", 5.0, 37.0, 60.0, 93.0),
            new Box("mesopotamian", 29.0, 38.5, 38.0, 49.0),
            new Box("southeast_asian", -12.0, 24.0, 92.0, 143.0),
            new Box("east_asian", 17.0, 55.0, 95.0, 150.0),
            // Xinjiang, Tibet and the Pamirs: a gap between the Persianate and East Asian boxes
            // that left Khotan and East Turkestan unnamed (2026-10-24).
            new Box("persianate", 25.0, 50.0, 75.0, 95.0),
            new Box("north_african", 14.0, 37.5, -18.0, 35.0),
            new Box("persianate", 12.0, 50.0, 35.0, 75.5),
            new Box("african", -36.0, 20.0, -20.0, 52.0),
            new Box("americas", -60.0, 75.0, -170.0, -30.0),
            new Box("mediterranean", 25.0, 72.0, -12.0, 45.0),
    };

    /**
     * The pool for a point, or {@code null} when no box holds it.
     *
     * <p>Public so the check can ask the same question the game asks, and so a future data pass can
     * scan for places in no box.
     */
    public static String poolFor(double lat, double lon) {
        for (Box box : BOXES) {
            if (box.holds(lat, lon)) {
                return box.pool();
            }
        }
        return null;
    }

    /**
     * The pool for a civilization id, from the centre of the cells it ruled.
     *
     * @return a pool id, or {@code null} when the register does not know where this polity was
     */
    public static String cultureFor(String civId) {
        if (civId == null || civId.isBlank()) {
            return null;
        }
        try {
            return RulerRegister.get().extentOf(civId.trim())
                    .map(extent -> poolFor(extent.lat(), extent.lon()))
                    .orElse(null);
        } catch (RuntimeException e) {
            // Names are decoration on a person who must still spawn. A register that cannot answer
            // leaves the generic pool, which is exactly where this class started.
            return null;
        }
    }

    /** How many boxes there are, for the check report. */
    public static int boxCount() {
        return BOXES.length;
    }
}
