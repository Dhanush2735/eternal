package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 42 (D7, social order): <b>how a society here was divided, and who may speak to whom</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>Nothing. The plan records D7 as "old code (class system)", and there is no class, no caste
 * and no guild anywhere in the mod: a search for any of the three finds {@code MoneyPlaces} and
 * nothing else. Every person in the mod has stood in the same relation to every other, in 3000 BC
 * and in 1900 alike.
 *
 * <h3>What this register answers</h3>
 *
 * <p>Asked for a point and a year it gives an {@link Order}: the strata, highest first, with what
 * share of people stood in each; whether birth fixes it ({@link Order#caste()}); whether a craft is
 * held by a guild; whether a man can leave the order he was born to; and — the half of D7 that is
 * not a list of ranks — <b>who may speak to whom</b>.
 *
 * <h3>Keyed the way the culture atlas is keyed</h3>
 *
 * <p>{@code CountryGrid.codeAt(lat, lon)} gives an ISO3 code and the row whose {@code where} holds
 * that code wins; the highest {@code priority} wins where two rows cover the same year. That is the
 * same shape as the {@code units} table of {@code culture.json.gz}, for the same reason.
 *
 * <h3>Every row is estimated</h3>
 *
 * <p><b>No source states how much of a society was brahmin or shudra in a given century</b>, and no
 * source gives a number for how a man addresses his better. Every share and every rule of address
 * is this table's reading of the reference work named on the row, and the register says so rather
 * than printing a figure as though it had been measured.
 *
 * <p>Twelve orders cannot be the whole world in every year. Where no row covers a place the
 * register answers with the {@code default}, and {@link Order#isDefault()} says that it did: a
 * register that shrugs has failed, but a register that passes a guess off as a record has lied.
 */
public final class SocialOrder {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/social_order.json.gz";

    /** Who a man of this stratum may address uninvited. */
    public static final int MAY_NEVER = 0;
    public static final int MAY_WHEN_SPOKEN_TO = 1;
    public static final int MAY_OWN_AND_BELOW = 2;
    public static final int MAY_ANYONE = 3;

    /**
     * One order of a society.
     *
     * @param share the share of people in it (this table's reading, not a measurement)
     * @param may   who he may address uninvited: 0 never, 1 when spoken to, 2 his own order and
     *              beneath, 3 anyone
     */
    public record Stratum(String name, String who, double share, String line, int may) {

        /** "the third estate — those who work" */
        public String display() {
            return name + " - " + who;
        }

        /** The four degrees, in words. */
        public String mayLine() {
            return switch (may) {
                case MAY_NEVER -> "he is not spoken to and does not speak first";
                case MAY_WHEN_SPOKEN_TO -> "he answers when he is spoken to, and not before";
                case MAY_OWN_AND_BELOW -> "he speaks to his own order and to those beneath it";
                default -> "he speaks to anyone, and is answered";
            };
        }
    }

    /** A society and the orders it was divided into, for a span of years. */
    public record Order(String id, String name, int start, int end, int priority, String source,
                        String mobility, String guilds, String caste, String speak,
                        List<Stratum> strata, boolean isDefault) {

        /** The highest order first, as the table wrote them. */
        public Stratum top() {
            return strata.isEmpty() ? null : strata.get(0);
        }

        /** The stratum a person of this share of the population stands in, highest first. */
        public Stratum stratumAt(double roll) {
            if (strata.isEmpty()) {
                return null;
            }
            double r = roll < 0.0d ? 0.0d : roll > 1.0d ? 1.0d : roll;
            double seen = 0.0d;
            for (Stratum s : strata) {
                seen += s.share();
                if (r <= seen) {
                    return s;
                }
            }
            return strata.get(strata.size() - 1);
        }

        /**
         * Whether a man of one order may open a conversation with a man of another.
         *
         * <p>Three degrees answer yes to everyone; two answers yes to his own order and below; one
         * answers only when spoken to; and naught answers to nobody at all — a man outside the
         * varnas is not addressed by anyone inside them, in either direction.
         */
        public boolean mayAddress(Stratum from, Stratum to) {
            if (from == null || to == null) {
                return true;                       // unknown, and the register will not invent a slight
            }
            return switch (from.may()) {
                case MAY_NEVER -> false;
                case MAY_WHEN_SPOKEN_TO -> false;  // he may answer, not open
                case MAY_OWN_AND_BELOW -> to.may() <= from.may();
                default -> true;
            };
        }

        /** Why, in words. */
        public String addressLine(Stratum from, Stratum to) {
            if (from == null || to == null) {
                return "nobody here stands on ceremony with a stranger";
            }
            if (mayAddress(from, to)) {
                return from.name() + " may speak to " + to.name() + ": " + from.mayLine();
            }
            return from.name() + " does not open a conversation with " + to.name()
                    + ": " + from.mayLine();
        }

        /** The line, with the source it was read from where there is one. */
        public String line() {
            StringBuilder sb = new StringBuilder(name).append(": ");
            sb.append(String.join(", ", strata.stream().map(Stratum::name).toList()));
            sb.append(". ").append(speak);
            if (source != null && !source.isBlank()) {
                sb.append(" [read from ").append(source).append(']');
            }
            return sb.toString();
        }
    }

    private static final SocialOrder INSTANCE = new SocialOrder();

    private final List<Order> orders = new ArrayList<>();
    private final Map<String, List<Order>> byCountry = new LinkedHashMap<>();
    private Order fallback;
    private int countryCount;
    private boolean loaded;
    private String source = "missing";

    private SocialOrder() {}

    public static SocialOrder get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public String source() {
        ensureLoaded();
        return source;
    }

    public int orderCount() {
        ensureLoaded();
        return orders.size();
    }

    public int countryCount() {
        ensureLoaded();
        return countryCount;
    }

    public boolean hasData() {
        ensureLoaded();
        return !orders.isEmpty() && fallback != null;
    }

    /** Every order in the pack, for the checks. */
    public List<Order> orders() {
        ensureLoaded();
        return Collections.unmodifiableList(orders);
    }

    /** The order of a country in a year, or the default when no row covers it. */
    public Order at(String countryCode, int year) {
        ensureLoaded();
        Order best = null;
        for (Order o : byCountry.getOrDefault(countryCode == null ? "" : countryCode, List.of())) {
            if (o.start() != Integer.MIN_VALUE && year < o.start()) {
                continue;
            }
            if (o.end() != Integer.MAX_VALUE && year >= o.end()) {
                continue;
            }
            if (best == null || o.priority() > best.priority()) {
                best = o;
            }
        }
        return best != null ? best : fallback;
    }

    /** The order here, in this year: {@code CountryGrid} says which country, the pack says how it stood. */
    public Order hereAt(double lat, double lon, int year) {
        return at(CountryGrid.get().codeAt(lat, lon), year);
    }

    // ------------------------------------------------------------------ the file

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = SocialOrder.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            JsonObject doc;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                doc = JsonParser.parseReader(r).getAsJsonObject();
            }
            install(doc);
            source = RESOURCE;
        } catch (IOException | IllegalStateException | NullPointerException e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(JsonObject doc) {
        for (JsonElement e : arr(doc, "orders")) {
            Order o = order(e.getAsJsonObject(), false);
            orders.add(o);
            for (String c : countriesOf(e.getAsJsonObject())) {
                byCountry.computeIfAbsent(c, k -> new ArrayList<>()).add(o);
            }
        }
        fallback = order(doc.getAsJsonObject("default"), true);
        countryCount = byCountry.size();
    }

    private static List<String> countriesOf(JsonObject o) {
        List<String> out = new ArrayList<>();
        for (JsonElement w : arr(o, "where")) {
            out.add(w.getAsString());
        }
        return out;
    }

    private static Order order(JsonObject o, boolean isDefault) {
        List<Stratum> strata = new ArrayList<>();
        for (JsonElement e : arr(o, "strata")) {
            JsonObject s = e.getAsJsonObject();
            strata.add(new Stratum(str(s, "name"), str(s, "who"), s.get("share").getAsDouble(),
                    str(s, "line"), s.get("may").getAsInt()));
        }
        JsonElement start = o.get("start");
        JsonElement end = o.get("end");
        return new Order(str(o, "id"), str(o, "name"),
                start == null || start.isJsonNull() ? Integer.MIN_VALUE : start.getAsInt(),
                end == null || end.isJsonNull() ? Integer.MAX_VALUE : end.getAsInt(),
                o.get("priority").getAsInt(), str(o, "source"), str(o, "mobility"),
                o.has("guilds") && !o.get("guilds").isJsonNull() ? o.get("guilds").getAsString() : null,
                o.has("caste") && !o.get("caste").isJsonNull() ? o.get("caste").getAsString() : null,
                str(o, "speak"), List.copyOf(strata), isDefault);
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonArray() ? new JsonArray() : e.getAsJsonArray();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        ensureLoaded();
        return String.format(Locale.ROOT, "SocialOrder[%d orders over %d countries, %s]",
                orders.size(), countryCount, source);
    }
}
