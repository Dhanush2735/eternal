package com.eternalengine.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>A fork a player caused.</b> Stage 2 of player-changes-history, 2026-10-27.
 *
 * <h2>Why this is the piece that unlocks the rest</h2>
 *
 * <p>The three shipped scenarios are all {@code "author": "UNSIGNED DRAFT"}, so {@link Divergence#enterable()}
 * refuses every one of them - correctly, because a historical claim nobody signed is a claim nobody can check.
 * A fork caused by a player has an author by definition: <b>the player</b>. That is the whole trick. Player
 * causation is what makes a fork signable, and therefore enterable, without anybody forging a signature on
 * somebody else's history.
 *
 * <h2>The rules this ledger enforces</h2>
 *
 * <ol>
 *   <li><b>Signed.</b> An unnamed player cannot open a fork: there would be nobody to attribute it to.</li>
 *   <li><b>Said plainly.</b> A fork must state what the player did. An empty description is not an event.</li>
 *   <li><b>Dated and placed.</b> Every fork carries the year, month and day it happened and where.</li>
 *   <li><b>Once only.</b> The same player doing the same thing on the same day at the same place is one fork,
 *       not two. Replays and double-fires must not multiply history.</li>
 *   <li><b>Consequences are not invented here.</b> The ledger records the cause. What it changed is either
 *       authored later, or the fork says openly that nothing has been written for those years yet - which is
 *       exactly what {@link DivergedHistory#accounts} reports for it.</li>
 *   <li><b>The sky is untouched.</b> Nothing in this class can reach the astronomy; a test asserts it.</li>
 * </ol>
 */
public final class ForkLedger {

    /** One player-caused fork. */
    public record Fork(String id, String player, double lat, double lon, int year, int month, int day,
                       String what) {

        /** The day key the rest of the engine uses, so a fork sorts with everything else. */
        public int key() {
            return com.eternalengine.knowledge.KnowledgeDate.key(year, month, day);
        }

        /** How the fork reads in a book. Always attributed: a fork with no name would be a rumour. */
        public String line() {
            return year + "." + String.format(Locale.ROOT, "%02d", month) + "."
                    + String.format(Locale.ROOT, "%02d", day) + " - " + what + " (" + player + ")"
                    + " [fork " + id + "]";
        }

        /** A player fork is authored by its player, by construction. */
        public String provenance() {
            return "authored";
        }
    }

    /** The outcome of trying to open a fork: either a fork, or the reason there is none. */
    public record Result(Fork fork, List<String> refused) {
        public boolean ok() {
            return fork != null && refused.isEmpty();
        }

        public String why() {
            return String.join("; ", refused);
        }
    }

    private static final List<Fork> LEDGER = new ArrayList<>();

    private ForkLedger() {
    }

    /**
     * A player did something, here and now, that the record does not contain.
     *
     * <p>Returns the fork, or the reasons the ledger refused to open one. Nothing is silently dropped: every
     * refusal names its own rule.
     */
    public static synchronized Result cause(String player, double lat, double lon, int year, int month, int day,
                                            String what) {
        List<String> refused = new ArrayList<>();
        if (player == null || player.isBlank()) {
            refused.add("a fork nobody signed cannot be entered: say who caused it");
        }
        if (what == null || what.isBlank()) {
            refused.add("a fork with no description is not an event: say what happened");
        }
        if (month < 0 || month > 12 || day < 0 || day > 31) {
            refused.add("that is not a date: " + year + "-" + month + "-" + day);
        }
        if (Math.abs(lat) > 90.0d || Math.abs(lon) > 180.0d) {
            refused.add("that is not a place on Earth: " + lat + ", " + lon);
        }
        if (!refused.isEmpty()) {
            return new Result(null, refused);
        }
        // Rule 4: the same player, the same day, the same square, the same act is one fork.
        for (Fork f : LEDGER) {
            if (f.player().equalsIgnoreCase(player) && f.key() == keyOf(year, month, day)
                    && Math.abs(f.lat() - lat) < 0.05d && Math.abs(f.lon() - lon) < 0.05d
                    && f.what().equalsIgnoreCase(what)) {
                refused.add("already recorded as fork " + f.id() + ": the same act on the same day at the same"
                        + " place is one fork, not two");
                return new Result(null, refused);
            }
        }
        String id = "player-" + Math.abs((player + lat + lon + year + month + day + what).hashCode()) % 1000000;
        Fork fork = new Fork(id, player, lat, lon, year, month, day, what.trim());
        LEDGER.add(fork);
        return new Result(fork, List.of());
    }

    private static int keyOf(int year, int month, int day) {
        return com.eternalengine.knowledge.KnowledgeDate.key(year, month, day);
    }

    /** Every fork a player has caused, oldest first. */
    public static synchronized List<Fork> all() {
        List<Fork> out = new ArrayList<>(LEDGER);
        out.sort((a, b) -> Integer.compare(a.key(), b.key()));
        return out;
    }

    public static synchronized java.util.Optional<Fork> at(String id) {
        for (Fork f : LEDGER) {
            if (f.id().equals(id)) {
                return java.util.Optional.of(f);
            }
        }
        return java.util.Optional.empty();
    }

    public static synchronized int size() {
        return LEDGER.size();
    }

    /** Test and save hooks: the ledger is the world's, so a save must be able to carry it. */
    public static synchronized void clear() {
        LEDGER.clear();
    }

    // ------------------------------------------------------------------ save and load

    /** The ledger as JSON, so it can live in a save beside wars and outbreaks. */
    public static synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("id", "eternal-player-forks");
        root.addProperty("format", 1);
        root.addProperty("rule", "every fork names the player who caused it; consequences are never invented here");
        JsonArray arr = new JsonArray();
        for (Fork f : all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("player", f.player());
            o.addProperty("lat", f.lat());
            o.addProperty("lon", f.lon());
            o.addProperty("year", f.year());
            o.addProperty("month", f.month());
            o.addProperty("day", f.day());
            o.addProperty("what", f.what());
            arr.add(o);
        }
        root.add("forks", arr);
        root.addProperty("count", arr.size());
        return root.toString();
    }

    /** Load a saved ledger. Refuses rather than guesses: a fork with no player is not loaded. */
    public static synchronized int fromJson(String json) {
        LEDGER.clear();
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("forks")) {
                JsonObject o = el.getAsJsonObject();
                String player = o.get("player").getAsString();
                if (player == null || player.isBlank()) {
                    continue;                       // rule 1 holds across a save and load too
                }
                LEDGER.add(new Fork(o.get("id").getAsString(), player, o.get("lat").getAsDouble(),
                        o.get("lon").getAsDouble(), o.get("year").getAsInt(), o.get("month").getAsInt(),
                        o.get("day").getAsInt(), o.get("what").getAsString()));
            }
        } catch (Exception e) {
            return LEDGER.size();
        }
        return LEDGER.size();
    }

    /**
     * The fork as a {@link Divergence}, so the existing two-account view works on it unchanged.
     *
     * <p>The consequence is the player's own act, marked {@code [plausible]} because it is exactly that: what
     * this timeline says happened instead. Its years are the fork's own year onwards, and
     * {@link DivergedHistory} will say plainly that the record carries nothing of its own for them.
     */
    public static Divergence asDivergence(Fork f) {
        return Divergence.playerFork(f.id(), "What " + f.player() + " did at " + f.year(), f.year(),
                f.player(), f.what());
    }
}
