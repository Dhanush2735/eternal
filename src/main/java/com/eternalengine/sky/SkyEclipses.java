package com.eternalengine.sky;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B23): <b>every eclipse of five thousand years, and what a given place saw of it</b>.
 *
 * <p>{@code history/sky_eclipses.bin.gz} (built by {@code worldfill/build_sky_eclipses.py} from
 * NASA's Five Millennium Catalogs, public domain) holds 11,898 solar eclipses from 1999 BCE to
 * 3000 CE and 12,064 lunar eclipses over the same span.
 *
 * <p>The solar half does not store a list of paths. It stores <b>Besselian elements</b> — the
 * polynomials that say where the Moon's shadow axis crosses the fundamental plane, hour by hour —
 * so that a place's own circumstances can be computed rather than looked up. That is the whole
 * reason the pack is 1.6 MB instead of a hundred times that, and the reason the register can say
 * "it was 97 per cent total here" for a village nobody drew a path through.
 *
 * <p><b>Delta-T is the part that matters and the part everyone omits.</b> The Earth's rotation is
 * not a clock: it has been slipping against the dynamical time the elements are written in for as
 * long as there are records, and the offset is now 69 seconds and was two and a half hours in 500
 * BCE. The elements give the shadow's position in Terrestrial Time; the ground is turning in
 * Universal Time. Subtracting ΔT is what puts the shadow on the right side of the planet — for the
 * eclipse of Thales (585 BCE) it is five hours of longitude, and without it that eclipse lands
 * seventy-seven degrees away, in the middle of the Pacific.
 *
 * <p>The magnitude reported is NASA's own measure: 1.0 is exactly total, above 1.0 is deeper than
 * total (the Moon more than covers the Sun), and below 0 means the Sun was not up there at all.
 * The geometry reproduces NASA's published magnitudes to 1e-4.
 *
 * <p><b>What the pack cannot say: the hour of day, to the minute.</b> NASA's reference instant is
 * not stored, so the register reports the date and estimates the hour from the shadow's hour angle
 * at greatest eclipse. That estimate is within the equation of time — a quarter of an hour at
 * most — and it is labelled as an estimate wherever it is shown.
 */
public final class SkyEclipses {

    public static final String FILE = "/data/eternalengine/eternal/history/sky_eclipses.bin.gz";
    private static final int MAGIC = 0x45434C53; // "ECLS"
    private static final int VERSION = 1;

    /** Every eclipse's elements are valid over the same three hours either side of greatest. */
    public static final double TMIN = -3.0d;
    public static final double TMAX = 3.0d;
    public static final double FLATTENING = 1.0d / 298.257223563d;

    public static final int SOLAR_PARTIAL = 0;
    public static final int SOLAR_ANNULAR = 1;
    public static final int SOLAR_TOTAL = 2;
    public static final int SOLAR_HYBRID = 3;

    public static final int LUNAR_PENUMBRAL = 0;
    public static final int LUNAR_PARTIAL = 1;
    public static final int LUNAR_TOTAL = 2;

    private static final int SOLAR_FIELDS = 25;
    private static final int SOLAR_RECORD = SOLAR_FIELDS * 4;
    private static final int LUNAR_RECORD = 8 * 4;

    private static volatile SkyEclipses instance;

    private final List<Solar> solar;
    private final List<Lunar> lunar;
    private final int solarFromYear;
    private final int solarToYear;
    private final int lunarFromYear;
    private final int lunarToYear;
    private final String problem;

    /** One solar eclipse: its date, its kind, the depth NASA records, and its Besselian elements. */
    public record Solar(int date, int code, double nasaMagnitude,
                        double x0, double x1, double x2, double x3,
                        double y0, double y1, double y2, double y3,
                        double d0, double d1, double d2,
                        double mu0, double mu1, double mu2,
                        double l10, double l11, double l12,
                        double l20, double l21, double l22,
                        double tanF1, double deltaT) {

        public int year() {
            return date / 10000 - 2000;
        }

        public int month() {
            return (date / 100) % 100;
        }

        public int day() {
            return date % 100;
        }

        public String type() {
            return switch (code) {
                case SOLAR_TOTAL -> "total";
                case SOLAR_ANNULAR -> "annular";
                case SOLAR_HYBRID -> "hybrid";
                default -> "partial";
            };
        }

        public String dateLabel() {
            int y = year();
            return day() + " " + SkyComets.Comet.yearLabel(y);
        }

        public double x(double t) {
            return x0 + t * (x1 + t * (x2 + t * x3));
        }

        public double y(double t) {
            return y0 + t * (y1 + t * (y2 + t * y3));
        }

        public double d(double t) {
            return d0 + t * (d1 + t * d2);
        }

        public double mu(double t) {
            return mu0 + t * (mu1 + t * mu2);
        }

        public double l1(double t) {
            return l10 + t * (l11 + t * l12);
        }

        public double l2(double t) {
            return l20 + t * (l21 + t * l22);
        }
    }

    /** One lunar eclipse: its date, its kind, how deep it went, and where the Moon stood. */
    public record Lunar(int date, int code, double umbralMagnitude, double penumbralMagnitude,
                        double zenithLat, double zenithLon, double durationTotalMin,
                        double durationPartialMin) {

        public int year() {
            return date / 10000 - 2000;
        }

        public int month() {
            return (date / 100) % 100;
        }

        public int day() {
            return date % 100;
        }

        public String type() {
            return switch (code) {
                case LUNAR_TOTAL -> "total";
                case LUNAR_PARTIAL -> "partial";
                default -> "penumbral";
            };
        }

        public boolean isTotal() {
            return code == LUNAR_TOTAL;
        }
    }

    /** What one place saw of one solar eclipse. */
    public record Sighting(Solar eclipse, double magnitude, double hourFromGreatest,
                           double utHours, boolean visible) {

        /** "total", "97% of the Sun covered", "not up here". */
        public String howMuch() {
            if (!visible) {
                return "not visible here";
            }
            if (magnitude >= 1.0d) {
                return "total" + (magnitude > 1.0d
                        ? " (" + String.format(Locale.ROOT, "%.2f", magnitude) + ")"
                        : "");
            }
            return String.format(Locale.ROOT, "%.0f%%", magnitude * 100.0d) + " of the Sun covered";
        }

        public String sentence() {
            return "A " + eclipse.type() + " eclipse of the Sun, " + eclipse.dateLabel() + ": "
                    + howMuch() + ".";
        }
    }

    private SkyEclipses(List<Solar> solar, List<Lunar> lunar, int sf, int st, int lf, int lt,
                        String problem) {
        this.solar = List.copyOf(solar);
        this.lunar = List.copyOf(lunar);
        this.solarFromYear = sf;
        this.solarToYear = st;
        this.lunarFromYear = lf;
        this.lunarToYear = lt;
        this.problem = problem;
    }

    public static SkyEclipses get() {
        SkyEclipses e = instance;
        if (e == null) {
            synchronized (SkyEclipses.class) {
                if (instance == null) {
                    instance = load();
                }
                e = instance;
            }
        }
        return e;
    }

    public boolean loaded() {
        return problem == null && !solar.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int solarCount() {
        return solar.size();
    }

    public int lunarCount() {
        return lunar.size();
    }

    public int solarFromYear() {
        return solarFromYear;
    }

    public int solarToYear() {
        return solarToYear;
    }

    public int lunarFromYear() {
        return lunarFromYear;
    }

    public int lunarToYear() {
        return lunarToYear;
    }

    public List<Solar> solar() {
        return solar;
    }

    public List<Lunar> lunar() {
        return lunar;
    }

    // ------------------------------------------------------------- the questions ----------

    /** Every solar eclipse of this one year. Two is common; five happens. */
    public List<Solar> solarInYear(int year) {
        List<Solar> out = new ArrayList<>();
        for (Solar e : solar) {
            if (e.year() == year) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    /** Every lunar eclipse of this one year. */
    public List<Lunar> lunarInYear(int year) {
        List<Lunar> out = new ArrayList<>();
        for (Lunar e : lunar) {
            if (e.year() == year) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    /**
     * What this place saw of this eclipse. A magnitude below zero means the Sun was below the
     * horizon there; 1.0 is exactly total; above 1.0 the Moon more than covered the Sun.
     */
    public Sighting sighting(Solar e, double lat, double lon) {
        double best = -1.0d;
        double bestT = 0.0d;
        int steps = 160;
        for (int i = 0; i <= steps; i++) {
            double t = TMIN + (TMAX - TMIN) * i / steps;
            double[] plane = observerPlane(lat, lon, e.d(t), e.mu(t), e.deltaT());
            double xi = plane[0];
            double eta = plane[1];
            double zeta = plane[2];
            if (zeta <= 0.0d) {
                continue;                     // the Sun is not up there
            }
            double dist = Math.hypot(e.x(t) - xi, e.y(t) - eta);
            double l1p = e.l1(t) - zeta * e.tanF1();
            double l2p = e.l2(t) - zeta * e.tanF1();
            double denom = l1p + l2p;
            double m = dist <= Math.abs(l2p) ? (l1p - l2p) / denom : (l1p - dist) / denom;
            if (m > best) {
                best = m;
                bestT = t;
            }
        }
        return new Sighting(e, best, bestT, utHoursAt(e, bestT), best > 0.0d);
    }

    /**
     * Where this eclipse was deepest, and when. The greatest point is found by minimising the
     * axis's distance from the Earth's centre, which reproduces NASA's own gamma to five decimals.
     *
     * @return {latitude, longitude, hours from the catalogue's reference instant}
     */
    public double[] greatestPoint(Solar e) {
        double bestT = TMIN;
        double bestQ = Double.MAX_VALUE;
        int steps = 4000;
        for (int i = 0; i <= steps; i++) {
            double t = TMIN + (TMAX - TMIN) * i / steps;
            double q = e.x(t) * e.x(t) + e.y(t) * e.y(t);
            if (q < bestQ) {
                bestQ = q;
                bestT = t;
            }
        }
        double step = (TMAX - TMIN) / steps;
        for (int k = 0; k < 40; k++) {
            step *= 0.5d;
            for (double t : new double[]{bestT - step, bestT + step}) {
                double q = e.x(t) * e.x(t) + e.y(t) * e.y(t);
                if (q < bestQ) {
                    bestQ = q;
                    bestT = t;
                }
            }
        }
        double[] ll = axisPoint(e.x(bestT), e.y(bestT), e.d(bestT), e.mu(bestT), e.deltaT());
        return new double[]{ll[0], ll[1], bestT};
    }

    /**
     * The hour of day at greatest eclipse, Universal Time. The pack does not store NASA's
     * reference instant, so this is recovered from the shadow axis's own hour angle — the Sun is
     * on the meridian at the point where the eclipse is central, and that gives the clock. It is
     * right to within the equation of time, about a quarter of an hour, and no better.
     */
    public double utHoursAt(Solar e, double t) {
        double muUt = e.mu(t) - e.deltaT() / 240.0d;
        return ((12.0d + muUt / 15.0d) % 24.0d + 24.0d) % 24.0d;
    }

    /**
     * The solar eclipses this place could see in a stretch of years, deepest first. This is the
     * question a settlement asks: when is the next one, and will it go dark here?
     */
    public List<Sighting> solarSeen(double lat, double lon, int fromYear, int toYear,
                                    double minMagnitude) {
        List<Sighting> out = new ArrayList<>();
        for (Solar e : solar) {
            int y = e.year();
            if (y < fromYear || y > toYear) {
                continue;
            }
            Sighting s = sighting(e, lat, lon);
            if (s.visible() && s.magnitude() >= minMagnitude) {
                out.add(s);
            }
        }
        out.sort((a, b) -> Double.compare(b.magnitude(), a.magnitude()));
        return List.copyOf(out);
    }

    /** The next eclipse this place will see, or null when the catalogue ends first. */
    public Sighting nextSolarSeen(double lat, double lon, int fromYear, double minMagnitude,
                                  int withinYears) {
        Sighting best = null;
        for (Solar e : solar) {
            int y = e.year();
            if (y < fromYear || y > fromYear + withinYears) {
                continue;
            }
            Sighting s = sighting(e, lat, lon);
            if (s.visible() && s.magnitude() >= minMagnitude) {
                return s;
            }
        }
        return best;
    }

    /** Whether the Moon was above the horizon here during this lunar eclipse: half the Earth. */
    public boolean lunarVisible(Lunar e, double lat, double lon) {
        return haversineKm(lat, lon, e.zenithLat(), e.zenithLon()) <= 10000.0d;
    }

    /** The lunar eclipses this place could watch in a stretch of years, in order. */
    public List<Lunar> lunarSeen(double lat, double lon, int fromYear, int toYear) {
        List<Lunar> out = new ArrayList<>();
        for (Lunar e : lunar) {
            int y = e.year();
            if (y >= fromYear && y <= toYear && lunarVisible(e, lat, lon)) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    /** One sentence for a player standing here in this year, or empty when the sky held nothing. */
    public String describe(double lat, double lon, int year) {
        StringBuilder sb = new StringBuilder();
        for (Solar e : solarInYear(year)) {
            Sighting s = sighting(e, lat, lon);
            if (s.visible()) {
                sb.append(article(e.type())).append(' ').append(e.type())
                        .append(" eclipse of the Sun on ").append(e.dateLabel())
                        .append(": ").append(s.howMuch()).append(", at about ")
                        .append(hourLabel(s.utHours())).append(". ");
            }
        }
        for (Lunar e : lunarInYear(year)) {
            if (e.isTotal() && lunarVisible(e, lat, lon)) {
                sb.append("A total eclipse of the Moon on ").append(e.day()).append(' ')
                        .append(SkyComets.Comet.yearLabel(e.year())).append(", lasting ")
                        .append((int) Math.round(e.durationTotalMin()))
                        .append(" minutes from full to full. ");
            }
        }
        return sb.toString().trim();
    }

    /** "a" or "an", so the record can say "an annular eclipse" rather than "a annular". */
    private static String article(String word) {
        return "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "An" : "A";
    }

    private static String hourLabel(double ut) {
        int h = (int) Math.floor(ut);
        int m = (int) Math.round((ut - h) * 60.0d);
        if (m == 60) {
            h = (h + 1) % 24;
            m = 0;
        }
        return String.format(Locale.ROOT, "%02d:%02d", h, m) + " Universal Time";
    }

    // ---------------------------------------------------------------- the geometry ----------

    /** Where the shadow axis meets the ground, from the elements at one instant. */
    static double[] axisPoint(double x, double y, double dDeg, double muDeg, double dtS) {
        double zeta = Math.sqrt(Math.max(0.0d, 1.0d - x * x - y * y));
        double d = Math.toRadians(dDeg);
        double lam = Math.toRadians(-(muDeg - dtS / 240.0d));
        double[] z = {Math.cos(d) * Math.cos(lam), Math.cos(d) * Math.sin(lam), Math.sin(d)};
        double[] yn = {-z[2] * z[0], -z[2] * z[1], 1.0d - z[2] * z[2]};
        double n = norm(yn);
        double[] yh = {yn[0] / n, yn[1] / n, yn[2] / n};
        double[] xh = cross(yh, z);
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) {
            p[i] = x * xh[i] + y * yh[i] + zeta * z[i];
        }
        double latGc = Math.toDegrees(Math.asin(Math.max(-1.0d, Math.min(1.0d, p[2]))));
        double lon = Math.toDegrees(Math.atan2(p[1], p[0]));
        double lat = Math.toDegrees(Math.atan(
                Math.tan(Math.toRadians(latGc)) / Math.pow(1.0d - FLATTENING, 2.0d)));
        return new double[]{lat, lon};
    }

    /**
     * A place's (xi, eta, zeta) in the fundamental plane at one instant. zeta is how far the place
     * is above that plane, towards the Sun: positive means the Sun is up, which is the only case in
     * which an eclipse can be seen there at all.
     */
    static double[] observerPlane(double latDeg, double lonDeg, double dDeg, double muDeg,
                                  double dtS) {
        double latGc = Math.atan(Math.tan(Math.toRadians(latDeg)) * Math.pow(1.0d - FLATTENING, 2.0d));
        double d = Math.toRadians(dDeg);
        double lam = Math.toRadians(-(muDeg - dtS / 240.0d));
        double[] z = {Math.cos(d) * Math.cos(lam), Math.cos(d) * Math.sin(lam), Math.sin(d)};
        double[] yn = {-z[2] * z[0], -z[2] * z[1], 1.0d - z[2] * z[2]};
        double n = norm(yn);
        double[] yh = {yn[0] / n, yn[1] / n, yn[2] / n};
        double[] xh = cross(yh, z);
        double cl = Math.cos(latGc);
        double[] p = {cl * Math.cos(Math.toRadians(lonDeg)),
                cl * Math.sin(Math.toRadians(lonDeg)), Math.sin(latGc)};
        // The place's coordinates in the fundamental plane: its unit vector dotted with each of
        // the three basis vectors in turn. (Not with the columns: the three axes are xh, yh and z,
        // and xi, eta, zeta are the components along them, which is the other way round from
        // building a point from them as axisPoint does.)
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[0] += p[i] * xh[i];
            out[1] += p[i] * yh[i];
            out[2] += p[i] * z[i];
        }
        return out;
    }

    private static double norm(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0d) * Math.sin(dp / 2.0d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2.0d) * Math.sin(dl / 2.0d);
        return 2.0d * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    // -------------------------------------------------------------------- the load ----------

    private static SkyEclipses load() {
        try (InputStream raw = SkyEclipses.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            byte[] buf;
            try (GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw, 1 << 16))) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] chunk = new byte[1 << 16];
                int got;
                while ((got = gz.read(chunk)) > 0) {
                    bos.write(chunk, 0, got);
                }
                buf = bos.toByteArray();
            }
            if (buf.length < 36) {
                return empty(FILE + ": too short to be an eclipse pack");
            }
            int magic = i32le(buf, 0);
            int ver = i32le(buf, 4);
            if (magic != MAGIC) {
                return empty(FILE + ": not an eclipse pack");
            }
            if (ver != VERSION) {
                return empty(FILE + ": version " + ver);
            }
            int nSolar = i32le(buf, 8);
            int nLunar = i32le(buf, 12);
            int solarFrom = i32le(buf, 16);
            int solarTo = i32le(buf, 20);
            int lunarFrom = i32le(buf, 24);
            int lunarTo = i32le(buf, 28);
            int bodyLen = i32le(buf, 32);
            int off = 36;
            if (nSolar < 0 || nLunar < 0 || bodyLen != nSolar * SOLAR_RECORD
                    || buf.length < off + bodyLen + nLunar * LUNAR_RECORD) {
                return empty(FILE + ": record counts do not fit the file");
            }
            List<Solar> sol = new ArrayList<>(nSolar);
            for (int i = 0; i < nSolar; i++) {
                int p = off + i * SOLAR_RECORD;
                sol.add(new Solar(
                        i32le(buf, p), i32le(buf, p + 4), i32le(buf, p + 8) / 1000.0d,
                        i32le(buf, p + 12) / 1e9d, i32le(buf, p + 16) / 1e9d,
                        i32le(buf, p + 20) / 1e9d, i32le(buf, p + 24) / 1e9d,
                        i32le(buf, p + 28) / 1e9d, i32le(buf, p + 32) / 1e9d,
                        i32le(buf, p + 36) / 1e9d, i32le(buf, p + 40) / 1e9d,
                        i32le(buf, p + 44) / 1e6d, i32le(buf, p + 48) / 1e6d,
                        i32le(buf, p + 52) / 1e6d,
                        i32le(buf, p + 56) / 1e6d, i32le(buf, p + 60) / 1e6d,
                        i32le(buf, p + 64) / 1e6d,
                        i32le(buf, p + 68) / 1e7d, i32le(buf, p + 72) / 1e7d,
                        i32le(buf, p + 76) / 1e7d,
                        i32le(buf, p + 80) / 1e7d, i32le(buf, p + 84) / 1e7d,
                        i32le(buf, p + 88) / 1e7d,
                        i32le(buf, p + 92) / 1e7d, i32le(buf, p + 96)));
            }
            int loff = off + bodyLen;
            List<Lunar> lun = new ArrayList<>(nLunar);
            for (int i = 0; i < nLunar; i++) {
                int p = loff + i * LUNAR_RECORD;
                lun.add(new Lunar(
                        i32le(buf, p), i32le(buf, p + 4),
                        i32le(buf, p + 8) / 1000.0d, i32le(buf, p + 12) / 1000.0d,
                        i32le(buf, p + 16) / 100.0d, i32le(buf, p + 20) / 100.0d,
                        i32le(buf, p + 24) / 10.0d, i32le(buf, p + 28) / 10.0d));
            }
            if (sol.isEmpty()) {
                return empty(FILE + " held no eclipses");
            }
            return new SkyEclipses(sol, lun, solarFrom, solarTo, lunarFrom, lunarTo, null);
        } catch (Exception e) { // the mod must run without this file
            return empty(FILE + ": " + e);
        }
    }

    /** The pack is little-endian, so it is read by hand rather than with DataInputStream. */
    private static int i32le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }

    private static SkyEclipses empty(String problem) {
        return new SkyEclipses(List.of(), List.of(), 0, 0, 0, 0, problem);
    }
}
