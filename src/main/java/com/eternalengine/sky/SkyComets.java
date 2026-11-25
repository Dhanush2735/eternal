package com.eternalengine.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B23): <b>the great comets</b>, and the year each was in the sky.
 *
 * <p>These are the comets bright enough to be seen by people who were not looking for them, which
 * is exactly why they were written down: a comet you need a telescope to find leaves no record.
 * Halley appears twelve times, from 613 BC to 1986. The Great Comet of 371 BC was reported by
 * Aristotle and Ephorus. Caesar's comet of 44 BC — the one that was taken for his soul — is here,
 * and so is the Great Comet of 1811, which gave its name to a vintage of wine that is still
 * talked about.
 *
 * <p>{@code history/sky_comets.json.gz} (built by {@code worldfill/build_sky_comets.py} from
 * English Wikipedia's table of great comets, CC BY-SA 4.0). Years are astronomical, as the rest of
 * the mod counts them: 44 BC is {@code -43}.
 *
 * <p><b>The rule for a caller: a comet is news for the months around its perihelion, not for the
 * year.</b> It is the only sky event in the pack that a farmer can see for weeks on end, and the
 * one most likely to be read as an omen, so it belongs in the news a settlement hears.
 */
public final class SkyComets {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/sky_comets.json.gz";

    /** How many months either side of perihelion a comet is worth mentioning. */
    public static final int SEASON_MONTHS = 4;

    private static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

    private static volatile SkyComets instance;

    private final List<Comet> comets;
    private final String problem;

    /** One comet: its name, the year (and where the record keeps it, the month and day) of
     *  perihelion, how bright it became, and what the record says of it. */
    public record Comet(String name, int year, int month, int day, double magnitude, String note) {

        /** -1 when the record gives no brightness. */
        public boolean hasMagnitude() {
            return magnitude >= 0.0d;
        }

        public String date() {
            if (month <= 0 || month > 12) {
                return yearLabel(year);
            }
            if (day <= 0) {
                return MONTHS[month - 1] + " " + yearLabel(year);
            }
            return day + " " + MONTHS[month - 1] + " " + yearLabel(year);
        }

        public static String yearLabel(int year) {
            return year <= 0 ? (1 - year) + " BC" : "AD " + year;
        }
    }

    private SkyComets(List<Comet> comets, String problem) {
        this.comets = List.copyOf(comets);
        this.problem = problem;
    }

    public static SkyComets get() {
        SkyComets c = instance;
        if (c == null) {
            synchronized (SkyComets.class) {
                if (instance == null) {
                    instance = load();
                }
                c = instance;
            }
        }
        return c;
    }

    public boolean loaded() {
        return problem == null && !comets.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return comets.size();
    }

    public List<Comet> comets() {
        return comets;
    }

    /** Every comet of this one year. */
    public List<Comet> inYear(int year) {
        List<Comet> out = new ArrayList<>();
        for (Comet c : comets) {
            if (c.year() == year) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** The comets of this year and of the season either side of it — what the sky was showing. */
    public List<Comet> inSeason(int year) {
        List<Comet> out = new ArrayList<>();
        for (Comet c : comets) {
            int gap = c.year() - year;
            if (gap == 0) {
                out.add(c);
                continue;
            }
            // A comet of last December is still in the sky this January; one of this November
            // was talked about from midsummer. A year either side is close enough to be news.
            if (Math.abs(gap) <= 1) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** The comets of a stretch of years, in order. */
    public List<Comet> between(int fromYear, int toYear) {
        List<Comet> out = new ArrayList<>();
        for (Comet c : comets) {
            if (c.year() >= fromYear && c.year() <= toYear) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** The next comet after this year, or null when the record runs out. */
    public Comet nextAfter(int year) {
        Comet best = null;
        for (Comet c : comets) {
            if (c.year() > year && (best == null || c.year() < best.year())) {
                best = c;
            }
        }
        return best;
    }

    public boolean anyIn(int year) {
        return !inYear(year).isEmpty();
    }

    /** One sentence for a player standing here in this year, or empty when the sky was empty. */
    public String describe(int year) {
        List<Comet> now = inYear(year);
        if (now.isEmpty()) {
            return "";
        }
        Comet c = now.get(0);
        StringBuilder sb = new StringBuilder("A comet: ").append(c.name());
        if (nameHasYear(c)) {
            // ten of them are named for their year already ("Great Comet of 371 BC"), and saying
            // it twice reads like a stammer; only the month is news
            if (c.month() > 0 && c.month() <= MONTHS.length) {
                sb.append(", brightest in ").append(MONTHS[c.month() - 1]);
            }
        } else if (c.month() > 0) {
            sb.append(", at its brightest in ").append(c.date());
        } else {
            sb.append(", ").append(c.date());
        }
        if (c.hasMagnitude()) {
            sb.append(", magnitude ").append(String.format(java.util.Locale.ROOT, "%.1f", c.magnitude()));
        }
        String note = c.note() == null ? "" : c.note().trim();
        if (!note.isEmpty()) {
            sb.append(". ").append(note);
        }
        if (sb.charAt(sb.length() - 1) != '.') {
            sb.append('.');
        }
        return sb.toString();
    }

    /**
     * Whether the comet's own name says which year it was. The records write it two ways round
     * ("Great Comet of 371 BC" and "Great Comet of 178 AD"), so both are looked for.
     */
    private static boolean nameHasYear(Comet c) {
        String n = c.name() == null ? "" : c.name();
        String label = Comet.yearLabel(c.year());
        String swapped = label.startsWith("AD ") ? label.substring(3) + " AD" : label;
        return n.contains(label) || n.contains(swapped);
    }

    private static SkyComets load() {
        List<Comet> out = new ArrayList<>();
        try (InputStream raw = SkyComets.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new SkyComets(List.of(), "resource " + RESOURCE + " absent");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (JsonElement el : root.getAsJsonArray("comets")) {
                JsonArray r = el.getAsJsonArray();
                // [name, year, month or 0, day or 0, magnitude x100 or null, note]
                double mag = r.get(4).isJsonNull() ? -1.0d : r.get(4).getAsDouble() / 100.0d;
                out.add(new Comet(r.get(0).getAsString(), r.get(1).getAsInt(), r.get(2).getAsInt(),
                        r.get(3).getAsInt(), mag, r.get(5).getAsString()));
            }
            out.sort((a, b) -> Integer.compare(a.year(), b.year()));
            if (out.isEmpty()) {
                return new SkyComets(List.of(), RESOURCE + " held no comets");
            }
            return new SkyComets(out, null);
        } catch (Exception e) {
            return new SkyComets(List.of(), "resource " + RESOURCE + " failed: " + e.getMessage());
        }
    }
}
