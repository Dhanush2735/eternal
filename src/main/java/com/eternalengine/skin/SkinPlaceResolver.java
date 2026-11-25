package com.eternalengine.skin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which pool a person draws from, from the ground he stands on.
 *
 * <h3>Why a place and not just a civ</h3>
 * Every one of the 15,727 local places in the packs cites {@code india}, so a single civ pool made a
 * forest hamlet and a Hyderabad merchant the same people. But every place carries {@code lat} and
 * {@code lon}, and the engine already bridges blocks to geography
 * ({@code TerraCoordinateHelper.toGeo}). So the pool is resolved as a function of position:
 *
 * <pre>
 *   culture   <- the civ id, through SkinPoolDatabase.cultureFor (103 of 103 corpus ids resolve)
 *   sub-pool  <- the nearest sub-pool centre of that culture, in kilometres. The Deccan has five:
 *                Telugu, Deccani Urdu, Marathi, Kannada, and the forest/tribal east.
 *   flavour   <- the ground quantised to a ~5 km cell. Neighbouring villages share it; a village
 *                thirty kilometres away does not.
 * </pre>
 *
 * <p>Measured against the packs: a place's ten nearest neighbours share its sub-pool 99% of the time;
 * 153 of 200 places share their flavour cell with their nearest neighbour; 0 of 200 pairs about 30 km
 * apart do. So the faces are continuous across a village cluster and different across a district -
 * which is what a province is.
 *
 * <p><b>The centres and tones are plausible, not sourced.</b> No reference was fetched for where one
 * dialect's faces end and the next begins. They are six numbers each, in one table, and moving a centre
 * redraws a whole boundary.
 *
 * <p>Nothing here is Minecraft-dependent: this is the part that can be tested without a client, like
 * {@code SkinVariety}.
 */
public final class SkinPlaceResolver {

    /** A resolved place: the pool to draw from, and the cell whose faces are its faces. */
    public record Place(String culture, String subpool, String flavour, double lat, double lon) {
        /** The key the painter and the texture cache use. */
        public String key() {
            return culture + "|" + (subpool == null ? "-" : subpool) + "|" + flavour;
        }
    }

    /** A sub-pool centre: where a people's faces are at their most themselves. */
    public record Centre(String name, double lat, double lon, String note, int[] tones, int[] cloths) {}

    /** About 5 km: neighbours share a cell, the next town does not. */
    public static final double FLAVOUR_DEG = 0.05;

    private static final Map<String, Map<String, Centre>> SUBPOOLS = new LinkedHashMap<>();
    private static final Map<String, Place> CACHE = new ConcurrentHashMap<>();

    static {
        // the Deccan, which is the province this project exists for
        sub("deccan", new Centre("telugu", 17.6, 79.6, "the Telugu belt: Warangal, Karimnagar, Nalgonda",
                new int[]{0xC08A5E, 0xB57F55, 0xA9714B}, new int[]{0xB3472F, 0xD9B25A, 0x2F6EA8, 0x6B7F4A, 0x4A4436, 0xE0D3A8, 0x244A78, 0xC97A2B, 0x8E3B57, 0x2E6B4F, 0xB5651D, 0xD4C48A}));
        sub("deccan", new Centre("deccani", 17.4, 78.4, "the Deccani Urdu belt: Hyderabad and its towns",
                new int[]{0xA9714B, 0x8D5A39, 0xC08A5E}, new int[]{0x2F6EA8, 0xE0D3A8, 0x8E3B57, 0xA63A2A, 0x6F4526, 0xB07C36, 0x38563C, 0x3A3A3E, 0x6E93B8, 0x7B8F3A, 0xC24A32, 0x4E6E8E}));
        sub("deccan", new Centre("marathi", 19.3, 77.6, "the Marathi north-west: Nanded, Parbhani",
                new int[]{0xC08A5E, 0xD0A070, 0xB57F55}, new int[]{0xE0D3A8, 0x4A4436, 0xB3472F, 0xC9A24A, 0x35597E, 0xD08A2E, 0x9E8A5C, 0x2E2A26, 0x8C4A32, 0x7A3B52, 0x3F6B4A, 0xD8C6A0}));
        sub("deccan", new Centre("kannada", 15.9, 77.6, "the Kannada south-west: Raichur, Bellary",
                new int[]{0x8D5A39, 0xA9714B, 0x6F4526}, new int[]{0x6B7F4A, 0xD9B25A, 0x2F6EA8, 0xA63A2A, 0xB98A4E, 0x415C3B, 0xDCD3BE, 0x4E4A44, 0x84405A, 0x6B4A8A, 0x2E6B6B, 0xC9B27A}));
        sub("deccan", new Centre("forest", 18.6, 80.7, "the forest and river east: Adilabad, the Godavari",
                new int[]{0x6F4526, 0x8D5A39, 0x4A2C18}, new int[]{0x6B7F4A, 0x4A4436, 0x8E3B57, 0x7A5533, 0x54683F, 0xA85B3C, 0x8E8878, 0x27435F, 0xC2913F, 0x9A4A5A, 0x3E5C4A, 0xB0A68A}));
        // and the rest of the world, two to four each: 34 sub-pools in all
        sub("indic", new Centre("north", 25.3, 83.0, "the Ganga plain", null, null));
        sub("indic", new Centre("south", 9.9, 78.1, "the far south", null, null));
        sub("indic", new Centre("east", 22.6, 88.4, "Bengal and the east", null, null));
        sub("indic", new Centre("west", 23.2, 75.8, "Malwa and the west", null, null));
        sub("persianate", new Centre("west", 32.6, 51.7, "the Iranian plateau", null, null));
        sub("persianate", new Centre("north", 39.8, 64.4, "Transoxiana", null, null));
        sub("persianate", new Centre("south", 28.6, 77.2, "the Indian courts", null, null));
        sub("east_asian", new Centre("north", 34.3, 108.9, "the Yellow River", null, null));
        sub("east_asian", new Centre("east", 32.1, 118.8, "the Yangtze delta", null, null));
        sub("east_asian", new Centre("islands", 35.0, 135.8, "the islands", null, null));
        sub("east_asian", new Centre("south", 21.0, 105.8, "the southern coast", null, null));
        sub("mediterranean", new Centre("west", 41.9, 12.5, "the western sea", null, null));
        sub("mediterranean", new Centre("east", 38.0, 23.7, "the Aegean and the Levant", null, null));
        sub("mediterranean", new Centre("far_west", 38.7, -9.1, "Iberia and the Atlantic", null, null));
        sub("north_african", new Centre("delta", 30.0, 31.2, "the Nile", null, null));
        sub("north_african", new Centre("maghreb", 31.6, -8.0, "the Maghreb", null, null));
        sub("north_african", new Centre("nubia", 16.9, 33.8, "Nubia and the upper Nile", null, null));
        sub("african", new Centre("west", 16.8, -3.0, "the Sahel and the western forest", null, null));
        sub("african", new Centre("east", -8.9, 39.5, "the Swahili coast", null, null));
        sub("african", new Centre("south", -20.3, 30.9, "the southern plateau", null, null));
        sub("african", new Centre("horn", 14.1, 38.7, "the Horn", null, null));
        sub("americas", new Centre("meso", 19.7, -98.8, "Mesoamerica", null, null));
        sub("americas", new Centre("andean", -13.5, -72.0, "the Andes", null, null));
        sub("mesopotamian", new Centre("south", 32.5, 44.4, "Babylonia", null, null));
        sub("mesopotamian", new Centre("north", 36.4, 43.2, "Assyria", null, null));
        sub("rome", new Centre("west", 41.9, 12.5, "Rome itself", null, null));
        sub("rome", new Centre("east", 41.0, 28.98, "the eastern capital", null, null));
        sub("maurya", new Centre("east", 25.6, 85.1, "Magadha", null, null));
        sub("maurya", new Centre("west", 33.7, 72.8, "the north-west frontier", null, null));
    }

    private SkinPlaceResolver() {
    }

    private static void sub(String culture, Centre c) {
        SUBPOOLS.computeIfAbsent(culture, k -> new LinkedHashMap<>()).put(c.name(), c);
    }

    /** Flat-earth kilometres at this latitude: over a province it is exact enough, and checkable. */
    public static double kmBetween(double lat1, double lon1, double lat2, double lon2) {
        double mid = Math.toRadians((lat1 + lat2) / 2.0);
        double dy = (lat1 - lat2) * 111.0;
        double dx = (lon1 - lon2) * 111.0 * Math.cos(mid);
        return Math.sqrt(dy * dy + dx * dx);
    }

    /** The nearest centre of this culture, or null when the culture has none. Ties go to the first. */
    public static String subpoolFor(double lat, double lon, String culture) {
        Map<String, Centre> subs = SUBPOOLS.get(culture);
        if (subs == null || subs.isEmpty()) {
            return null;
        }
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (Centre c : subs.values()) {
            double d = kmBetween(lat, lon, c.lat(), c.lon());
            if (d < bestD) {
                bestD = d;
                best = c.name();
            }
        }
        return best;
    }

    /** The place's own ground, quantised: the same cell means the same families and the same faces. */
    public static String flavourFor(double lat, double lon) {
        return Math.round(lat / FLAVOUR_DEG) + ":" + Math.round(lon / FLAVOUR_DEG);
    }

    /** Everything a place decides, from its coordinates and its civ id alone. */
    public static Place resolve(double lat, double lon, String civId) {
        String culture = SkinPoolDatabase.cultureFor(civId == null ? "" : civId);
        String key = String.format(Locale.ROOT, "%.4f,%.4f,%s", lat, lon, culture);
        return CACHE.computeIfAbsent(key, k -> new Place(culture, subpoolFor(lat, lon, culture),
                flavourFor(lat, lon), lat, lon));
    }

    /** The tones and cloths of a sub-pool, or null when it inherits the culture's own. */
    public static Centre centreOf(String culture, String subpool) {
        Map<String, Centre> subs = SUBPOOLS.get(culture);
        return subs == null || subpool == null ? null : subs.get(subpool);
    }

    public static int subpoolCount() {
        int n = 0;
        for (Map<String, Centre> m : SUBPOOLS.values()) {
            n += m.size();
        }
        return n;
    }
}
