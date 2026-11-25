package com.eternalengine.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B22): <b>the naked-eye sky, for any year</b>.
 *
 * <p>{@code history/sky_stars.json.gz} (built by {@code worldfill/build_sky_stars.py} from the HYG
 * database v3.7, a merge of Hipparcos, the Yale Bright Star Catalogue and the Gliese catalogue,
 * CC BY-SA 4.0) holds every star of magnitude 6.0 or brighter plus the named stars a little
 * fainter: 5,666 of them, at epoch J2000 with their colour and their proper motion.
 *
 * <p>The pack is the sky as it stood on 2000 January 1.5, and <b>one pack serves every era because
 * the register turns it</b>. Two things move a star between 10,000 BCE and today:
 *
 * <ul>
 *   <li><b>Proper motion</b> — the star's own travel across the sky. Barnard's Star crosses
 *       fourteen degrees of it in the twelve thousand years this mod spans.</li>
 *   <li><b>Precession</b> — the Earth's axis swinging round, which is far bigger and is a function
 *       of the date rather than of the star, so it is applied here rather than tabulated. In 3000
 *       BCE the south celestial pole stood in Draco, and Thuban, not Polaris, was the pole star of
 *       Egypt.</li>
 * </ul>
 *
 * <p>Precession uses the IAU 2006 series for ζ, z and θ carried to the cubic term. <b>It is used
 * outside its stated range on purpose, and that is recorded rather than hidden</b>: the series is
 * fitted for a few centuries either side of J2000, and at 10,000 BCE it is being extrapolated
 * twenty times past its fit. The error there is of the order of a degree — small enough that the
 * constellations are recognisably the wrong ones in the right places, which is the truth of it,
 * and far better than drawing today's sky over Ur. A caller that needs the pole to the arcminute
 * should say so and be disappointed; a caller that needs "the Pleiades rise before the Sun in
 * October" has what it needs.
 *
 * <p>The Sun, the Moon and the planets are not in the pack: their places are computed from the
 * date, not tabulated.
 */
public final class SkyStars {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/sky_stars.json.gz";

    /** The instant the catalogue is given for: 2000 January 1.5 TT, Julian day 2451545.0. */
    public static final double J2000 = 2451545.0d;

    private static volatile SkyStars instance;

    private final List<Star> stars;
    private final String problem;

    /**
     * One star at epoch J2000. Proper motions are in milliarcseconds a year; {@code pmRa} is
     * μ-α-cos-δ, so it already carries the cos(dec) the change in right ascension needs.
     */
    public record Star(String name, double raDeg, double decDeg, double magnitude,
                       double colourIndex, double pmRaMas, double pmDecMas, String constellation) {

        public boolean isNamed() {
            return name != null && !name.isEmpty();
        }

        /** What it is called, or the constellation and a number when it has no name of its own. */
        public String label() {
            return isNamed() ? name
                    : (constellation == null || constellation.isEmpty() ? "an unnamed star"
                            : "a star of " + constellation);
        }

        /** Blue-white through orange to red, from the B-V colour index. */
        public String colour() {
            if (colourIndex < 0.0d) {
                return "blue-white";
            }
            if (colourIndex < 0.5d) {
                return "white";
            }
            if (colourIndex < 1.0d) {
                return "yellow";
            }
            return colourIndex < 1.4d ? "orange" : "red";
        }

        public double raHours() {
            return raDeg / 15.0d;
        }
    }

    /** Where a star stands over a place at an instant, as seen from the ground. */
    public record Placement(Star star, double raDeg, double decDeg, double altitudeDeg,
                            double azimuthDeg, boolean up) {

        /** North, south, east, west, and the quarters between them. */
        public String compass() {
            String[] pts = {"north", "north-east", "east", "south-east", "south", "south-west",
                    "west", "north-west"};
            int i = (int) Math.floor(((azimuthDeg % 360.0d) + 360.0d) % 360.0d / 45.0d + 0.5d) % 8;
            return pts[i];
        }

        /** "high in the south", "low over the north-west", "below the horizon". */
        public String where() {
            if (!up) {
                return "below the horizon";
            }
            String height = altitudeDeg > 60.0d ? "high" : altitudeDeg > 30.0d ? "well up"
                    : altitudeDeg > 10.0d ? "halfway up" : "low";
            return height + " in the " + compass();
        }

        public String sentence() {
            return star.label() + (star.isNamed() ? ", " + star.colour() : "") + ", is "
                    + where() + " (magnitude "
                    + String.format(java.util.Locale.ROOT, "%.1f", star.magnitude()) + ")";
        }
    }

    private SkyStars(List<Star> stars, String problem) {
        this.stars = List.copyOf(stars);
        this.problem = problem;
    }

    public static SkyStars get() {
        SkyStars s = instance;
        if (s == null) {
            synchronized (SkyStars.class) {
                if (instance == null) {
                    instance = load();
                }
                s = instance;
            }
        }
        return s;
    }

    public boolean loaded() {
        return problem == null && !stars.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return stars.size();
    }

    public List<Star> stars() {
        return stars;
    }

    public static double julianDay(int year, int month, int day, double hour) {
        int a = year <= 0 ? year + 1 : year;
        int y = a;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int b = year <= 0 ? 0 : (a / 400 - a / 100);
        return Math.floor(365.25d * (y + 4716)) + Math.floor(30.6001d * (m + 1)) + day + b
                - 1524.5d + hour / 24.0d;
    }

    /** Years from J2000, for the precession series and the proper motion. */
    public static double yearsFromJ2000(double jd) {
        return (jd - J2000) / 365.25d;
    }

    /** Centuries from J2000, the argument the IAU 2006 series is written in. */
    public static double centuriesFromJ2000(double jd) {
        return (jd - J2000) / 36525.0d;
    }

    /**
     * Precession, IAU 2006. Carries a J2000 right ascension and declination onto the mean equator
     * and equinox of the date, through the three angles ζ, z and θ.
     *
     * @return {right ascension degrees, declination degrees}
     */
    public static double[] precess(double raDeg, double decDeg, double jd) {
        double t = centuriesFromJ2000(jd);
        double t2 = t * t;
        double t3 = t2 * t;
        double zeta = Math.toRadians((2306.083227d * t + 0.298849d * t2 + 0.018018d * t3) / 3600.0d);
        double z = Math.toRadians((2306.077181d * t + 1.092734d * t2 + 0.018268d * t3) / 3600.0d);
        double theta = Math.toRadians((2004.191903d * t - 0.429493d * t2 - 0.041822d * t3) / 3600.0d);
        double ra = Math.toRadians(raDeg);
        double dec = Math.toRadians(decDeg);
        double a = Math.cos(dec) * Math.sin(ra + zeta);
        double b = Math.cos(theta) * Math.cos(dec) * Math.cos(ra + zeta) - Math.sin(theta) * Math.sin(dec);
        double c = Math.sin(theta) * Math.cos(dec) * Math.cos(ra + zeta) + Math.cos(theta) * Math.sin(dec);
        double raOut = Math.atan2(a, b) + z;
        double decOut = Math.asin(Math.max(-1.0d, Math.min(1.0d, c)));
        return new double[]{normalise360(Math.toDegrees(raOut)), Math.toDegrees(decOut)};
    }

    /** Where a star stands on the sky of this instant: precessed, with its proper motion added. */
    public static double[] placeOf(Star star, double jd) {
        double years = yearsFromJ2000(jd);
        double ra = star.raDeg() + (star.pmRaMas() * years / 3.6e6d)
                / Math.max(0.02d, Math.cos(Math.toRadians(star.decDeg())));
        double dec = star.decDeg() + star.pmDecMas() * years / 3.6e6d;
        return precess(normalise360(ra), dec, jd);
    }

    /**
     * Greenwich mean sidereal time in degrees, from the Julian day. This is what decides which
     * stars are overhead at a given hour, and it is the one thing in the register that has to be
     * right for "the sky tonight" to mean anything.
     */
    public static double gmstDeg(double jd) {
        double d = jd - J2000;
        double t = d / 36525.0d;
        double g = 280.46061837d + 360.98564736629d * d + 0.000387933d * t * t
                - t * t * t / 38710000.0d;
        return normalise360(g);
    }

    /** Where a star stands over a place at an instant, in altitude and azimuth. */
    public static Placement place(Star star, double lat, double lon, double jd) {
        double[] eq = placeOf(star, jd);
        double ra = eq[0];
        double dec = eq[1];
        double lst = normalise360(gmstDeg(jd) + lon);
        double h = Math.toRadians(normalise180(lst - ra));
        double phi = Math.toRadians(lat);
        double d = Math.toRadians(dec);
        double sinAlt = Math.sin(phi) * Math.sin(d) + Math.cos(phi) * Math.cos(d) * Math.cos(h);
        double alt = Math.asin(Math.max(-1.0d, Math.min(1.0d, sinAlt)));
        double cosAlt = Math.cos(alt);
        double az = Math.atan2(-Math.cos(d) * Math.sin(h),
                Math.cos(phi) * Math.sin(d) - Math.sin(phi) * Math.cos(d) * Math.cos(h));
        if (cosAlt < 1e-9d) {
            az = 0.0d;
        }
        return new Placement(star, ra, dec, Math.toDegrees(alt), normalise360(Math.toDegrees(az)),
                Math.toDegrees(alt) > 0.0d);
    }

    /** Every star above the horizon at this place and instant, brightest first. */
    public List<Placement> above(double lat, double lon, double jd, double minAltitudeDeg,
                                 double faintestMagnitude) {
        List<Placement> out = new ArrayList<>();
        for (Star s : stars) {
            if (s.magnitude() > faintestMagnitude) {
                continue;
            }
            Placement p = place(s, lat, lon, jd);
            if (p.up() && p.altitudeDeg() >= minAltitudeDeg) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.star().magnitude()));
        return List.copyOf(out);
    }

    /** The stars that never set at this latitude, on the sky of this instant. */
    public List<Star> circumpolar(double lat, double jd) {
        List<Star> out = new ArrayList<>();
        for (Star s : stars) {
            double dec = placeOf(s, jd)[1];
            if (lat >= 0.0d ? dec >= 90.0d - lat : dec <= -90.0d - lat) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    /** The stars that never rise at this latitude, on the sky of this instant. */
    public List<Star> neverRises(double lat, double jd) {
        List<Star> out = new ArrayList<>();
        for (Star s : stars) {
            double dec = placeOf(s, jd)[1];
            if (lat >= 0.0d ? dec <= -(90.0d - lat) : dec >= 90.0d + lat) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    /** How many of the pack's stars this latitude can ever see, at some hour of the year. */
    public int everVisible(double lat, double jd) {
        return count() - neverRises(lat, jd).size();
    }

    /**
     * The brightest named star near the pole on the sky of this instant — the pole star of this
     * era, which is not always Polaris. In 3000 BCE it was Thuban.
     */
    public Placement poleStar(double jd, double faintestMagnitude) {
        // Precession turns the whole sphere, so the pole of the sky this era is simply declination
        // +90 on the precessed frame: whichever star lands nearest it is the pole star. (Precessing
        // the J2000 pole gives where the OLD pole now sits, a good 26 degrees off the new one at
        // 3000 BCE -- a tempting and wrong shortcut, since the two are only the same at J2000.)
        double best = Double.MAX_VALUE;
        Placement bestP = null;
        for (Star s : stars) {
            if (s.magnitude() > faintestMagnitude) {
                continue;
            }
            double[] eq = placeOf(s, jd);
            double d = 90.0d - eq[1];          // angular distance from the pole of date
            if (d < best) {
                best = d;
                bestP = new Placement(s, eq[0], eq[1], 0.0d, 0.0d, true);
            }
        }
        return bestP;
    }

    /** Degrees between two points on the sky. */
    public static double angularDistanceDeg(double ra1, double dec1, double ra2, double dec2) {
        double d1 = Math.toRadians(dec1);
        double d2 = Math.toRadians(dec2);
        double dr = Math.toRadians(ra1 - ra2);
        double c = Math.sin(d1) * Math.sin(d2) + Math.cos(d1) * Math.cos(d2) * Math.cos(dr);
        return Math.toDegrees(Math.acos(Math.max(-1.0d, Math.min(1.0d, c))));
    }

    /**
     * One sentence for a player standing here at this instant: the brightest thing overhead and
     * the pole star of the era.
     */
    public String describe(double lat, double lon, double jd) {
        List<Placement> up = above(lat, lon, jd, 10.0d, 4.0d);
        if (up.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Overhead: ");
        for (int i = 0; i < Math.min(3, up.size()); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(up.get(i).sentence());
        }
        Placement pole = poleStar(jd, 4.0d);
        if (pole != null) {
            sb.append(". The pole star of this age is ").append(pole.star().label());
        }
        return sb.toString();
    }

    public static double normalise360(double deg) {
        double v = deg % 360.0d;
        return v < 0.0d ? v + 360.0d : v;
    }

    public static double normalise180(double deg) {
        double v = normalise360(deg);
        return v > 180.0d ? v - 360.0d : v;
    }

    private static SkyStars load() {
        List<Star> out = new ArrayList<>();
        try (InputStream raw = SkyStars.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new SkyStars(List.of(), "resource " + RESOURCE + " absent");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (JsonElement el : root.getAsJsonArray("stars")) {
                JsonArray r = el.getAsJsonArray();
                // [name or "", ra x1000, dec x1000, magnitude x100, B-V x100,
                //  pm-ra x10 mas/yr, pm-dec x10 mas/yr, constellation]
                out.add(new Star(
                        r.get(0).getAsString(),
                        r.get(1).getAsDouble() / 1000.0d,
                        r.get(2).getAsDouble() / 1000.0d,
                        r.get(3).getAsDouble() / 100.0d,
                        r.get(4).getAsDouble() / 100.0d,
                        r.get(5).getAsDouble() / 10.0d,
                        r.get(6).getAsDouble() / 10.0d,
                        r.get(7).getAsString()));
            }
            if (out.isEmpty()) {
                return new SkyStars(List.of(), RESOURCE + " held no stars");
            }
            return new SkyStars(out, null);
        } catch (Exception e) {
            return new SkyStars(List.of(), "resource " + RESOURCE + " failed: " + e.getMessage());
        }
    }
}
