package com.eternalengine.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Step 37 (C29): <b>what people here know, or believe</b>.
 *
 * <p>The mod has always known what happened and who ruled. It has never had the faintest idea
 * what anybody <i>thought</i>. That is the difference between a chronicle and a place: walk into
 * a village in 1348 and the first thing you meet is not a date, it is what the villagers believe
 * about the sickness in the next valley.
 *
 * <p>{@code history/beliefs.json.gz} is a hand-written table of 29 beliefs, each with the place
 * and the years it was held, who held it, and the Wikipedia article it came from — on the
 * discipline of step 36's disaster supplement: <b>no source, no row</b>.
 *
 * <h2>Who held it</h2>
 *
 * <p>That matters as much as the belief. C29 asks what <b>educated</b> people believed, and that
 * was often not what the street believed, and was sometimes not what a handful of scholars
 * believed either — Aryabhata's rotating earth was held against the consensus of India for seven
 * centuries.
 *
 * <pre>
 *   EVERYONE   held in the fields as much as in the schools
 *   EDUCATED   the learned consensus: the physicians, the clerks, the priests, the astronomers
 *   SCHOLARS   a minority of the learned, against the consensus of their own time
 * </pre>
 *
 * <h2>What it is not</h2>
 *
 * <p>It is not an argument that the past was stupid. Several of these were entirely reasonable
 * given what could be observed at the time — bad air really does go with fever, a candle really
 * does go out under a glass — and that is precisely why they were believed and why they lasted.
 * A row that sneers is a row that lies, so none of them do.
 *
 * <p>Each row also carries what a player from today would say. Sometimes that is the same as the
 * belief: the table is about what was believed, and sometimes what was believed was right, and
 * the interesting thing is <i>when</i> it became the common view.
 *
 * <p><b>The rule for a caller: ask this before anyone opens their mouth about why something
 * happened.</b> A physician who has never heard of a germ is not an ignorant physician; he is a
 * physician of 1348, and he is about to tell you about the air.
 */
public final class Beliefs {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/beliefs.json.gz";

    /** Who held a belief. */
    public enum Who {
        /** Held in the fields as much as in the schools. */
        EVERYONE("everyone here says"),
        /** The learned consensus of the place. */
        EDUCATED("the learned here say"),
        /** A minority of the learned, against the consensus of their own time. */
        SCHOLARS("a few of the learned here hold");

        private final String phrase;

        Who(String phrase) {
            this.phrase = phrase;
        }

        /** "everyone here says", for the mouth of a sentence. */
        public String phrase() {
            return phrase;
        }
    }

    /**
     * One belief.
     *
     * @param belief what was held, in the plainest words
     * @param now    what a player from today would say
     * @param from   the first year it was held
     * @param to     the last, or {@link Integer#MAX_VALUE} where it is still held somewhere
     * @param boxes  the boxes it was held in, empty for the whole world
     * @param source the Wikipedia article it came from
     */
    public record Belief(String id, String belief, String now, int from, int to, Who who,
                         String source, String note, double[][] boxes) {

        boolean holds(double lat, double lon, int year) {
            if (from > year || to < year) {
                return false;
            }
            if (boxes == null || boxes.length == 0) {
                return true;
            }
            for (double[] b : boxes) {
                if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) {
                    return true;
                }
            }
            return false;
        }

        /** "Bad air brings the fever." */
        public String said() {
            String s = belief.trim();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1) + ".";
        }

        /** "You would say: the fever is carried by a mosquito, or in the water." */
        public String youWouldSay() {
            return "You would say: " + now + ".";
        }

        /** "The learned here say: illness is an imbalance of the four humours." */
        public String line() {
            String p = who.phrase();
            return Character.toUpperCase(p.charAt(0)) + p.substring(1) + ": " + belief + ".";
        }
    }

    private static final Beliefs INSTANCE = new Beliefs();
    private static final Beliefs EMPTY = new Beliefs();

    private boolean loaded;
    private String source = "missing";
    private List<Belief> beliefs = List.of();

    private Beliefs() {}

    /** The register, loaded from the shipped pack. */
    public static synchronized Beliefs get() {
        if (!INSTANCE.loaded) {
            INSTANCE.loaded = true;
            INSTANCE.loadResource();
        }
        return INSTANCE;
    }

    /** An empty register, for checks. */
    public static Beliefs empty() {
        return EMPTY;
    }

    private void loadResource() {
        try (InputStream in = Beliefs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(in, 1 << 16), StandardCharsets.UTF_8)) {
                install(JsonParser.parseReader(reader).getAsJsonObject());
                source = RESOURCE;
            }
        } catch (Exception e) {
            source = RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(JsonObject root) {
        List<Belief> out = new ArrayList<>();
        JsonArray arr = root.getAsJsonArray("beliefs");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                double[][] boxes = new double[0][];
                JsonArray bx = o.getAsJsonArray("boxes");
                if (bx != null) {
                    boxes = new double[bx.size()][];
                    for (int j = 0; j < bx.size(); j++) {
                        JsonArray b = bx.get(j).getAsJsonArray();
                        boxes[j] = new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                                b.get(2).getAsDouble(), b.get(3).getAsDouble()};
                    }
                }
                out.add(new Belief(
                        o.get("id").getAsString(),
                        o.get("belief").getAsString(),
                        o.get("now").getAsString(),
                        o.get("from").getAsInt(),
                        o.has("to") && !o.get("to").isJsonNull() ? o.get("to").getAsInt()
                                : Integer.MAX_VALUE,
                        Who.valueOf(o.get("who").getAsString().toUpperCase(Locale.ROOT)),
                        o.has("source") ? o.get("source").getAsString() : "",
                        o.has("note") ? o.get("note").getAsString() : "",
                        boxes));
            }
        }
        beliefs = List.copyOf(out);
    }

    public int count() {
        return beliefs.size();
    }

    public String source() {
        return source;
    }

    /** Every row, for checks and for the data-credits listing. */
    public List<Belief> all() {
        return beliefs;
    }

    /** Every belief held at a place in a year. */
    public List<Belief> at(double lat, double lon, int year) {
        List<Belief> out = new ArrayList<>();
        for (Belief b : beliefs) {
            if (b.holds(lat, lon, year)) {
                out.add(b);
            }
        }
        return out;
    }

    /** Every belief held at a place in a year by a particular kind of person. */
    public List<Belief> at(double lat, double lon, int year, Who who) {
        List<Belief> out = new ArrayList<>();
        for (Belief b : at(lat, lon, year)) {
            if (b.who() == who) {
                out.add(b);
            }
        }
        return out;
    }

    /** One belief by id, where it is held at a place in a year. */
    public Optional<Belief> held(String id, double lat, double lon, int year) {
        for (Belief b : at(lat, lon, year)) {
            if (b.id().equals(id)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * What someone of this standing would say people here believe. A court clerk quotes the
     * learned consensus; a farmer in the bazaar repeats what everyone says. Empty where nothing
     * is on record for the place and year, and empty is not permission to invent something.
     *
     * <p>This says where a belief was <b>held</b>, not where people were: a belief recorded for
     * the whole world therefore reaches the middle of the Pacific, where of course nobody held
     * it. Callers ask about settlements, where that does not arise.
     */
    public String says(String role, double lat, double lon, int year, long salt) {
        List<Belief> here = at(lat, lon, year);
        if (here.isEmpty()) {
            return "";
        }
        boolean learned = KnowledgeLevels.of(role) >= 2;
        Who want = learned ? Who.EDUCATED : Who.EVERYONE;
        List<Belief> pool = new ArrayList<>();
        for (Belief b : here) {
            if (b.who() == want) {
                pool.add(b);
            }
        }
        if (pool.isEmpty()) {
            // nobody of their standing has an opinion: fall back to whoever does
            pool = new ArrayList<>(here);
        }
        Belief b = pool.get((int) Math.floorMod(salt, (long) pool.size()));
        return learned ? "It is held that " + b.belief() + "." : b.line();
    }

    /**
     * The card body: what is believed here, and what you would say instead. Three at most, and
     * the ones held by the learned first, because that is what C29 asks after.
     */
    public String card(double lat, double lon, int year) {
        List<Belief> here = at(lat, lon, year);
        if (here.isEmpty()) {
            return "";
        }
        List<Belief> ordered = new ArrayList<>();
        for (Who w : new Who[]{Who.EDUCATED, Who.EVERYONE, Who.SCHOLARS}) {
            for (Belief b : here) {
                if (b.who() == w && ordered.size() < 3) {
                    ordered.add(b);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Belief b : ordered) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(b.line()).append("  ").append(b.youWouldSay());
        }
        return sb.toString();
    }
}
