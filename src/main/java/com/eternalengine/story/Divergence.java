package com.eternalengine.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>One player-caused fork in history.</b> The first half of "player changes history", 2026-10-27.
 *
 * <h2>What this is, and what it is not</h2>
 *
 * <p>The engine already had three authored what-if scenarios ({@code scenarios/what_if_*.json}), written under
 * the rule <i>"deletes real consequences rather than inventing events"</i>. Until now nothing could reach them
 * from play: they were files. This class is the model that makes a fork addressable, and {@link DivergedHistory}
 * is what answers questions inside one.
 *
 * <p><b>What is still blocked:</b> the world itself visibly changing (terrain, roads, buildings by year) needs
 * Terra++'s branch API - blocker P2 in {@code COMPLETE_100.md}, still at 0. So this is the <i>history</i> half of
 * player-caused divergence: the record, the accounts and the honesty rules. It is not the terrain half, and
 * nothing here pretends otherwise.
 *
 * <h2>The discipline, inherited from the scenario files</h2>
 *
 * <ul>
 *   <li>a fork never invents a wonder: every consequence either <b>removes</b> something the record holds or is
 *       marked as authored;</li>
 *   <li>a fork cannot touch a year before it happens - 1800 is the same in both timelines;</li>
 *   <li>a fork is <b>signed</b>. An unsigned scenario may be listed but may never be entered, because a claim
 *       nobody signed is a claim nobody can check. All three shipped scenarios are currently signed
 *       "UNSIGNED DRAFT", so {@link #enterable()} is false for all three, and the check says so out loud.</li>
 * </ul>
 */
public final class Divergence {

    /** The scenarios that exist today. Listed rather than discovered: the classpath cannot be walked portably. */
    private static final String[] FILES = {
            "what_if_gandhi_1948.json",
            "what_if_mumbai_26_11.json",
            "what_if_tesla_1943.json",
    };
    private static final String DIR = "/data/eternalengine/eternal/scenarios/";

    /** One consequence of the fork, as the scenario file states it. */
    public record Consequence(int year, String form, String name, String text, String provenance,
                              String source) {

        /** Whether the scenario marks this line as fact-driven rather than invented. */
        public boolean statesItsBasis() {
            return text.contains("[fact]") || text.contains("[plausible]") || !source.isBlank();
        }
    }

    private final String id;
    private final String title;
    private final int triggerYear;
    private final String triggerPin;
    private final String author;
    private final String provenance;
    private final List<Consequence> consequences;
    private final String discipline;

    private Divergence(String id, String title, int triggerYear, String triggerPin, String author,
                       String provenance, List<Consequence> consequences, String discipline) {
        this.id = id;
        this.title = title;
        this.triggerYear = triggerYear;
        this.triggerPin = triggerPin;
        this.author = author;
        this.provenance = provenance;
        this.consequences = List.copyOf(consequences);
        this.discipline = discipline;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int triggerYear() {
        return triggerYear;
    }

    public String triggerPin() {
        return triggerPin;
    }

    public String author() {
        return author;
    }

    public String provenance() {
        return provenance;
    }

    public String discipline() {
        return discipline;
    }

    public List<Consequence> consequences() {
        return consequences;
    }

    /**
     * Whether a player may enter this fork.
     *
     * <p>Three conditions, each of them earned: the fork is signed by a named person (not "UNSIGNED DRAFT"),
     * its provenance is {@code authored} (a fork is somebody's claim, never the record), and it has at least one
     * consequence. Today all three shipped scenarios fail the first.
     */
    public boolean enterable() {
        return author != null && !author.isBlank()
                && !author.toUpperCase(java.util.Locale.ROOT).contains("UNSIGNED")
                && "authored".equalsIgnoreCase(provenance)
                && !consequences.isEmpty();
    }

    /** Why it is not enterable, in words a player could be shown. Empty when it is. */
    public String notEnterableBecause() {
        if (author == null || author.isBlank() || author.toUpperCase(java.util.Locale.ROOT).contains("UNSIGNED")) {
            return "nobody has signed it, so nobody can be asked to stand behind it";
        }
        if (!"authored".equalsIgnoreCase(provenance)) {
            return "it does not say it is an authored fork (" + provenance + ")";
        }
        if (consequences.isEmpty()) {
            return "it changes nothing, so it is not a fork";
        }
        return "";
    }

    /**
     * A fork a player caused, made addressable in the same shape as a shipped scenario.
     *
     * <p>Added 2026-10-27 with {@link ForkLedger}. The difference that matters: this fork is <b>signed by the
     * player who caused it</b>, so {@link #enterable()} passes - unlike the three shipped scenarios, which are
     * unsigned drafts and are refused. The consequence is the player's act itself, marked authored because the
     * player is the author; nothing about what it changed is invented here, and {@link DivergedHistory} says so
     * for the years it touches.
     */
    public static Divergence playerFork(String id, String title, int year, String player, String what) {
        List<Consequence> cs = List.of(new Consequence(year, "PLAYER_ACT", what,
                "[plausible] " + what, "authored", ""));
        return new Divergence(id, title, year, "", player, "authored", cs,
                "a fork caused by a player; the consequence is the act itself, never an invented event");
    }

    // ------------------------------------------------------------------ loading

    private static List<Divergence> all;

    public static synchronized List<Divergence> all() {
        if (all == null) {
            List<Divergence> out = new ArrayList<>();
            for (String file : FILES) {
                Divergence d = read(file);
                if (d != null) {
                    out.add(d);
                }
            }
            all = List.copyOf(out);
        }
        return all;
    }

    public static java.util.Optional<Divergence> at(String id) {
        for (Divergence d : all()) {
            if (d.id().equals(id)) {
                return java.util.Optional.of(d);
            }
        }
        return java.util.Optional.empty();
    }

    private static Divergence read(String file) {
        try (InputStream in = Divergence.class.getResourceAsStream(DIR + file)) {
            if (in == null) {
                return null;
            }
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<Consequence> cs = new ArrayList<>();
            JsonArray ev = o.has("events") ? o.getAsJsonArray("events") : new JsonArray();
            for (JsonElement el : ev) {
                JsonObject e = el.getAsJsonObject();
                cs.add(new Consequence(
                        e.has("year") ? e.get("year").getAsInt() : 0,
                        str(e, "form"),
                        str(e, "name"),
                        str(e, "description"),
                        str(e, "provenance"),
                        str(e, "source")));
            }
            return new Divergence(str(o, "scenarioId"), str(o, "title"),
                    o.has("triggerYear") ? o.get("triggerYear").getAsInt() : 0,
                    str(o, "triggerPin"), str(o, "author"), str(o, "provenance"), cs,
                    str(o, "_discipline"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** For diagnostics: id -> title, in the order they load. */
    public static Map<String, String> index() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Divergence d : all()) {
            out.put(d.id(), d.title());
        }
        return out;
    }

    @Override
    public String toString() {
        return "fork " + id + " (" + triggerYear + "): " + title
                + (enterable() ? "" : " - not enterable: " + notEnterableBecause());
    }
}
