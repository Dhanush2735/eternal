package com.eternalengine.civilization;

import com.google.gson.stream.JsonReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 29 (system C28): how states were tied to each other, by year.
 *
 * <p>{@code history/state_relations.json.gz} (built by {@code worldfill/build_relations.py}) keeps alliances,
 * allegiance, vassals, shared rulers and the larger realms a state belonged to, from Cliopatria (the source of the
 * ruler register, so the names are the register's), and the members of military alliances from Wikidata (NATO from
 * 1949, the Warsaw Pact 1955-1991, the Central Powers ...). A state's card answers "Which states is it tied to?".
 */
public final class StateRelations {

    public static final String FILE = "/data/eternalengine/eternal/history/state_relations.json.gz";

    /** One tie: {@code a} to {@code b} in these years (inclusive). */
    public record Tie(String kind, String a, String b, int from, int to, int since) {
        boolean in(int year) {
            return from <= year && year <= to;
        }
    }

    private static volatile StateRelations instance;

    private final Map<String, List<Tie>> byA;
    private final Map<String, List<Tie>> byB;
    private final int size;
    private final String problem;

    private StateRelations(List<Tie> ties, String problem) {
        Map<String, List<Tie>> a = new HashMap<>();
        Map<String, List<Tie>> b = new HashMap<>();
        for (Tie t : ties) {
            a.computeIfAbsent(t.a(), k -> new ArrayList<>()).add(t);
            b.computeIfAbsent(t.b(), k -> new ArrayList<>()).add(t);
        }
        this.byA = a;
        this.byB = b;
        this.size = ties.size();
        this.problem = problem;
    }

    public static StateRelations get() {
        StateRelations s = instance;
        if (s == null) {
            synchronized (StateRelations.class) {
                if (instance == null) {
                    instance = load();
                }
                s = instance;
            }
        }
        return s;
    }

    public boolean loaded() {
        return problem == null && size > 0;
    }

    public String problem() {
        return problem;
    }

    public int size() {
        return size;
    }

    /** The ties of a state in a year, from its side ("It is a vassal of ...") and the other side ("... is its vassal"). */
    public List<Tie> tiesOf(String state, int year) {
        List<Tie> out = new ArrayList<>();
        for (Tie t : byA.getOrDefault(state, List.of())) {
            if (t.in(year)) {
                out.add(t);
            }
        }
        for (Tie t : byB.getOrDefault(state, List.of())) {
            if (t.in(year)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * "It is a vassal of the Holy Roman Empire. It is a member of NATO (since 1949)." At most four sentences; empty
     * when the data knows no tie that year. Many members of a realm are not listed from the realm's side.
     */
    public String line(String state, int year) {
        if (state == null || state.isBlank()) {
            return "";
        }
        Set<String> out = new LinkedHashSet<>();
        Set<String> overlords = new java.util.HashSet<>();
        for (Tie t : byA.getOrDefault(state, List.of())) {
            if (t.in(year) && (t.kind().equals("vassal") || t.kind().equals("allegiance"))) {
                overlords.add(t.b());
            }
        }
        for (Tie t : byA.getOrDefault(state, List.of())) {
            if (!t.in(year)) {
                continue;
            }
            String b = t.b();
            switch (t.kind()) {
                case "vassal" -> out.add("It is a vassal of " + the(b) + ".");
                case "allegiance" -> out.add("It owes allegiance to " + the(b) + ".");
                case "member" -> {
                    if (!overlords.contains(b)) {
                        out.add(realmLine(b));   // a vassal of the realm already says it belongs to it
                    }
                }
                case "union" -> out.add("It shares its ruler with " + the(b) + ".");
                case "alliance" -> out.add("It is allied with " + the(b) + ".");
                case "pact" -> out.add("It is a member of " + allianceName(b)
                        + (t.since() < year ? " (since " + t.since() + ")" : " (from this year)") + ".");
                default -> {
                }
            }
        }
        List<String> under = new ArrayList<>();
        for (Tie t : byB.getOrDefault(state, List.of())) {
            if (!t.in(year)) {
                continue;
            }
            String a = t.a();
            switch (t.kind()) {
                case "vassal" -> under.add(the(a) + " is its vassal.");
                case "allegiance" -> under.add(the(a) + " owes it allegiance.");
                case "union" -> out.add("It shares its ruler with " + the(a) + ".");
                case "alliance" -> out.add("It is allied with " + the(a) + ".");
                default -> {
                }
            }
        }
        for (String u : under) {
            out.add(Character.toUpperCase(u.charAt(0)) + u.substring(1));
        }
        List<String> list = new ArrayList<>(out);
        return String.join(" ", list.subList(0, Math.min(4, list.size())));
    }

    private static final java.util.regex.Pattern ORDER = java.util.regex.Pattern.compile(
            "(?i)\\b(order|crown|papacy|church|horde|confederacy)\\b");

    /** The register's article rule ("the Mughal Empire", "Joseon"), and "the" before an order or a crown too. */
    static String the(String name) {
        String s = EventNews.stateName(name);
        return s.equals(name) && ORDER.matcher(name).find() && !name.toLowerCase(java.util.Locale.ROOT).startsWith("the ")
                ? "the " + name : s;
    }

    private static final java.util.regex.Pattern GROUP = java.util.regex.Pattern.compile(
            "(?i)\\b(kingdoms|states|duchies|taifas|mahajanapadas|dynasties|principalities|city-states|beyliks)\\b");

    /** "It is part of the Holy Roman Empire." / "It is one of the Taifas of Iberia." */
    static String realmLine(String realm) {
        String shown = realm.equals("Warring States China") ? "Warring States of China" : realm;
        if (GROUP.matcher(shown).find()) {
            return "It is one of the " + shown + ".";
        }
        return "It is part of " + the(shown) + ".";
    }

    /** "NATO", "the Warsaw Pact", "the Central Powers". */
    static String allianceName(String name) {
        if (name.equals(name.toUpperCase(java.util.Locale.ROOT)) || name.toLowerCase(java.util.Locale.ROOT).startsWith("the ")) {
            return name;
        }
        return "the " + name;
    }

    private static StateRelations load() {
        List<Tie> ties = new ArrayList<>();
        String problem = null;
        try (InputStream raw = StateRelations.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                problem = FILE + " is missing";
            } else {
                JsonReader r = new JsonReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
                r.beginObject();
                while (r.hasNext()) {
                    if (!r.nextName().equals("relations")) {
                        r.skipValue();
                        continue;
                    }
                    r.beginArray();
                    while (r.hasNext()) {
                        r.beginArray();
                        String kind = r.nextString();
                        String a = r.nextString();
                        String b = r.nextString();
                        int from = r.nextInt();
                        int to = r.nextInt();
                        int since = r.hasNext() ? r.nextInt() : from;
                        ties.add(new Tie(kind, a, b, from, to, since));
                        while (r.hasNext()) {
                            r.skipValue();
                        }
                        r.endArray();
                    }
                    r.endArray();
                }
                r.endObject();
            }
        } catch (Exception e) {
            problem = "cannot read " + FILE + ": " + e;
            ties.clear();
        }
        return new StateRelations(ties, problem);
    }
}
