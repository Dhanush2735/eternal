package com.eternalengine.civilization;

import com.eternalengine.climate.ClimatePeriods;
import com.eternalengine.technology.TechnologyAtlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 43 (J4): <b>the sky and the air — smoke, haze and dust</b>.
 *
 * <p>There was nothing here. The {@code sky/} package is not this: it is comets, stars and
 * eclipses. Smoke, haze and dust exist nowhere in the mod.
 *
 * <h3>This register has no data pack, and that is deliberate</h3>
 *
 * <p>Parts 2 and 3 each needed a table, because the facts they carried were hand-written. J4 does
 * not: everything it says is derived from registers the mod already ships, so a pack would be a
 * second place for the same numbers to drift apart. Survey decision 7 was "no new data pack unless
 * a part proves it cannot be derived" — J4 could not prove it, so J4 derives.
 *
 * <ul>
 *   <li><b>Smoke</b> from the technology atlas, per country and dated: Manchester's coke ironworks
 *       1718, its cement works 1824, Beijing's 1905. Plus domestic hearths, which scale with
 *       population — <i>every</i> town before coal was smoky, and a Roman street reeked of wood
 *       smoke even though nothing there was industrial.</li>
 *   <li><b>Dust</b> from {@code ClimatePeriods}: droughts and failed monsoons, weighted by
 *       severity.</li>
 *   <li><b>Haze</b> from volcanic winters, weighted by severity. The register's own notes already
 *       describe the air — <i>"a dry fog dimmed the sun for eighteen months"</i> (536).</li>
 * </ul>
 *
 * <h3>The measurement that made severity matter</h3>
 *
 * <p>Taking the first active climate period naively gives <b>100% of the world "in drought" from
 * 2000 BC to AD 1800</b>. The reason is the <b>Meghalayan stage</b>, a real geological stage
 * (2250 BC to 1850 AD) recorded with {@code radiusKm = 0}, which {@code ClimatePeriods} reads as
 * <i>everywhere</i> — but at <b>severity 1</b>, the lowest in the register.
 *
 * <p>So severity is not decoration here; it is the difference between a geological background and
 * the worst year to be alive. Tambora is severity 8, the volcanic winter of 536 is 9, and the
 * Meghalayan stage is 1. Nothing below {@link #NOTICEABLE} is allowed to colour the air.
 *
 * <h3>What is a reading</h3>
 *
 * <p>Whether a drought was running, whether Tambora erupted, and whether the ironworks had reached
 * this country are all measured and dated. <b>How much smoke or dust that put in the air is this
 * register's reading</b> — no source states that Manchester's sky was a particular shade in 1850,
 * and the weights below are a choice, not a measurement.
 */
public final class Air {

    /** Below this severity a climate period is background, not weather you would notice. */
    public static final double NOTICEABLE = 5.0d;

    /** A technology that puts smoke up, and how much of it, once it has arrived. */
    private record SmokeSource(String id, double weight) {}

    private static final SmokeSource[] SMOKE = {
        // The great industrial sources. Weights are this register's reading of how much sky each
        // one costs: a cement works is the dirtiest thing in the list, a kiln the least.
        new SmokeSource("cement", 1.0d),
        new SmokeSource("open_hearth", 0.9d),
        new SmokeSource("bessemer", 0.9d),
        new SmokeSource("electric_steel", 0.7d),
        new SmokeSource("oxygen_steel", 0.7d),
        new SmokeSource("ironworks", 0.8d),
        new SmokeSource("steam_engine", 0.7d),
        new SmokeSource("gas_light", 0.3d),
        // Old and universal: the kiln and the smith's fire. Deliberately small. These are CRAFT
        // technologies with a share of 1.0 wherever they have arrived and arrival dates in the
        // Bronze Age, so weighting them like a cement works puts the whole world under smoke from
        // 3000 BC -- which is what the first version of this method did. A village with a smith
        // is not a smoky village.
        new SmokeSource("fired_brick", 0.05d),
        new SmokeSource("steel", 0.04d),
        new SmokeSource("iron", 0.04d),
    };

    /** The air of one place in one year. Each level is 0 (nothing) to 3 (it blots out the sun). */
    public record Here(double lat, double lon, int year, int smoke, int dust, int haze,
                       List<String> why, ClimatePeriods.Period worst) {

        /** Nothing at all in the air: the honest answer for most of the world, most of the time. */
        public boolean clear() {
            return smoke == 0 && dust == 0 && haze == 0;
        }

        /** The worst thing in the air, 0 to 3 (not the worst climate period - that is {@code worst()}). */
        public int level() {
            return Math.max(smoke, Math.max(dust, haze));
        }

        /** "clear", "smoky", "dusty", "hazy", or "thick" where several are at once. */
        public String sky() {
            int w = level();
            if (w == 0) {
                return "clear";
            }
            int howMany = (smoke > 0 ? 1 : 0) + (dust > 0 ? 1 : 0) + (haze > 0 ? 1 : 0);
            if (howMany >= 2 && w >= 2) {
                return "thick";
            }
            if (smoke >= dust && smoke >= haze) {
                return w >= 3 ? "black with smoke" : w >= 2 ? "smoky" : "a little hazy with smoke";
            }
            if (dust >= haze) {
                return w >= 3 ? "yellow with dust" : w >= 2 ? "dusty" : "a little dust in the air";
            }
            return w >= 3 ? "dim with volcanic haze" : w >= 2 ? "hazy" : "a thin haze";
        }

        /** The whole sky as one paragraph. */
        public String line() {
            if (clear()) {
                return "The air is clear.";
            }
            StringBuilder sb = new StringBuilder("The sky is " + sky() + ".");
            for (String w : why) {
                sb.append(" ").append(w);
            }
            return sb.toString();
        }
    }

    private Air() {}

    /**
     * The air at a place in a year.
     *
     * @param population how many people live here, which is how many hearths are smoking
     */
    public static Here hereAt(double lat, double lon, int year, SettlementRank rank, long population) {
        List<String> why = new ArrayList<>();
        TechnologyAtlas atlas = TechnologyAtlas.get();

        // ---- smoke ---------------------------------------------------------------------------
        double smokeScore = 0.0d;
        List<String> sources = new ArrayList<>();
        // Nobody living here means nothing is burning here. An empty stretch of steppe has clear
        // air whatever the technology atlas says has reached its country.
        if (population > 0) {
            for (SmokeSource s : SMOKE) {
                TechnologyAtlas.Tech t = atlas.tech(s.id());
                if (t == null) {
                    continue;
                }
                int arrived = atlas.arrivalAt(t, lat, lon);
                if (arrived >= TechnologyAtlas.NEVER || year < arrived) {
                    continue;
                }
                smokeScore += s.weight() * atlas.share(t, lat, lon, year, rank);
                sources.add(t.name);
            }
            // Hearth smoke. Every settlement burns something to cook and to keep warm, so a place
            // with people in it is never quite smoke-free -- only nearly so.
            double hearths = Math.min(0.45d, 0.08d + Math.log10(population) * 0.055d);
            smokeScore += hearths;
            sources.add("the cooking fires");
        }

        int smoke = level(smokeScore, 0.40d, 0.85d, 1.50d);
        if (smoke > 0 && !sources.isEmpty()) {
            boolean one = sources.size() == 1;
            why.add(String.format(Locale.ROOT, "%s %s.",
                    one ? sources.get(0) : String.join(", ", sources),
                    smoke >= 3 ? (one ? "has blackened the sky and everything in it"
                            : "have blackened the sky and everything in it")
                            : smoke >= 2 ? (one ? "hangs over the place" : "hang over the place")
                            : (one ? "puts a little smoke in the air"
                                   : "put a little smoke in the air")));
        }

        // ---- dust and haze, from the climate register -----------------------------------------
        int dust = 0;
        int haze = 0;
        ClimatePeriods.Period worst = null;
        for (ClimatePeriods.Period p : ClimatePeriods.get().active(lat, lon, year)) {
            if (worst == null) {
                worst = p;                       // active() sorts worst-first, so this is the worst
            }
            if (p.severity() < NOTICEABLE) {
                continue;                        // the Meghalayan stage is not weather
            }
            switch (p.type()) {
                case "DROUGHT", "MONSOON" -> {
                    int d = level((p.severity() - NOTICEABLE) / 3.0d, 0.15d, 0.5d, 0.9d);
                    if (d > dust) {
                        dust = d;
                    }
                }
                case "VOLCANIC" -> {
                    int h = level((p.severity() - NOTICEABLE) / 3.0d, 0.1d, 0.4d, 0.8d);
                    if (h > haze) {
                        haze = h;
                    }
                }
                case "PLUVIAL" -> {
                    // Rain washes the air. A pluvial does not put dust up; it keeps it down.
                    if (p.severity() >= NOTICEABLE) {
                        dust = Math.max(0, dust - 1);
                    }
                }
                default -> {
                    // WARMING, COOLING and SOLAR change the weather without filling the air.
                }
            }
        }
        if (worst != null) {
            if (dust >= 2) {
                why.add(worst.note());
            } else if (haze >= 2 && worst.note() != null && !worst.note().isBlank()) {
                why.add(worst.note());
            }
        }

        return new Here(lat, lon, year, smoke, dust, haze, why, worst);
    }

    /** The sky at a place and year, as one paragraph. */
    public static String lineAt(double lat, double lon, int year, SettlementRank rank, long population) {
        return hereAt(lat, lon, year, rank, population).line();
    }

    /** A score into 0-3, given three rising thresholds. */
    private static int level(double score, double a, double b, double c) {
        if (score >= c) {
            return 3;
        }
        if (score >= b) {
            return 2;
        }
        return score >= a ? 1 : 0;
    }
}
