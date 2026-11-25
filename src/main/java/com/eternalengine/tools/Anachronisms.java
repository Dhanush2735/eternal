package com.eternalengine.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 47 part 3 (E8, E22): <b>vanilla things that do not belong, and what a year may use.</b>
 *
 * <p>E22's line is <i>"enchanting, potions, diamond and netherite gear, ender pearls: locked or
 * renamed by era, as a setting"</i>, decided in turns 73 to 81 for swords and never built. E8 is
 * the other half: <i>"what can be made or used in a year — a time traveller setting for
 * exceptions"</i>.
 *
 * <h2>Three kinds of impossible thing, and three answers</h2>
 *
 * <p>Treating all of the game's fantasy as "not real, remove it" would delete Minecraft. Treating
 * all of it as allowed would make the year meaningless. So every row is one of three kinds:
 *
 * <ul>
 *   <li><b>{@code real}</b> — a real thing existed and the game's version is just better. Allowed
 *       from its year. A golden sword is <i>worse</i> than iron in vanilla and in history, which is
 *       one of the few places the game is accidentally right.</li>
 *   <li><b>{@code myth}</b> — people believed in it, so it existed as a belief and not as a
 *       function. <b>Allowed and renamed</b>: the object is real, the power is not. A brewing stand
 *       in 1300 is an <i>alchemist's bench</i>, and alchemy is real history: it runs from
 *       Hellenistic Egypt to Newton, who wrote more on it than on physics.</li>
 *   <li><b>{@code never}</b> — nothing like it has existed at any date. <b>Locked</b> in a strict
 *       world and allowed for a time traveller, who is then <i>seen</i> carrying it.</li>
 * </ul>
 *
 * <p><b>The middle kind is the one worth having</b>: it turns a blacklist into a translation. Six
 * of the nineteen rows are renamed rather than deleted.
 *
 * <h2>What this class does not do</h2>
 *
 * <p>It does not decide whether a potion <i>works</i>. That is step 38's health register and it is
 * the only part of this project that should ever answer it. This class decides whether an object
 * <i>may exist</i> here and now, and what to call it if it may.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Anachronisms {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/anachronisms.json.gz";

    private static volatile Anachronisms instance;

    /** The three kinds of impossible thing. */
    public enum Kind {
        /** A real thing existed, and the game's version is better. */
        REAL,
        /** People believed in it: real as a belief, not as a function. */
        MYTH,
        /** Nothing like it has existed at any date. */
        NEVER
    }

    /** What may be done about a row. */
    public enum Action {
        ALLOW,
        /** Keep the object, change the words: the object is real, the power is not. */
        RENAME,
        LOCK
    }

    /** How the world is being played. E8's time traveller is the second. */
    public enum Setting {
        /** The world obeys its year; a thing from the future is locked for everybody. */
        STRICT,
        /** The player is an exception, may keep their own time's things, and is SEEN doing it. */
        TRAVELLER;

        public static Setting of(String s) {
            if (s == null) return STRICT;
            String v = s.trim().toLowerCase(Locale.ROOT);
            return v.startsWith("trav") ? TRAVELLER : STRICT;
        }
    }

    /** The verdict on one object here and now. */
    public record Verdict(String id, Kind kind, Action action, boolean allowed,
                          @Nullable String asWhat, String why) {

        public boolean renamed() {
            return allowed && action == Action.RENAME && asWhat != null;
        }

        public String line() {
            if (!allowed) return id + ": not in this year. " + why;
            if (renamed()) return id + ": allowed, as " + asWhat;
            return id + ": allowed. " + why;
        }
    }

    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final String finding;
    private final String problem;

    private record Row(String id, Kind kind, Action action, @Nullable Integer year,
                       @Nullable String asWhat, String note, String source) {
    }

    private Anachronisms(@Nullable String problem) {
        this.finding = null;
        this.problem = problem;
    }

    private Anachronisms(JsonObject root) {
        for (JsonElement e : root.getAsJsonArray("rows")) {
            JsonObject o = e.getAsJsonObject();
            rows.put(str(o, "id"), new Row(str(o, "id"),
                    Kind.valueOf(str(o, "kind").toUpperCase(Locale.ROOT)),
                    Action.valueOf(str(o, "action").toUpperCase(Locale.ROOT)),
                    o.get("year").isJsonNull() ? null : o.get("year").getAsInt(),
                    o.get("asWhat").isJsonNull() ? null : str(o, "asWhat"),
                    str(o, "note"), str(o, "source")));
        }
        this.finding = root.has("finding") ? str(root, "finding") : null;
        this.problem = null;
    }

    public static Anachronisms get() {
        Anachronisms a = instance;
        if (a == null) {
            synchronized (Anachronisms.class) {
                a = instance;
                if (a == null) {
                    a = load();
                    instance = a;
                }
            }
        }
        return a;
    }

    private static Anachronisms load() {
        try (InputStream raw = Anachronisms.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) return new Anachronisms("resource " + RESOURCE + " is absent");
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Anachronisms(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            return new Anachronisms("resource " + RESOURCE + " failed: " + e);
        }
    }

    // ---------------------------------------------------------------------------------------- asking

    public boolean hasData() {
        return problem == null && !rows.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    /**
     * Judge an object for a year and a setting.
     *
     * <p><b>An object the register has never heard of is allowed</b>, and this is deliberate.
     * Blocking the unknown would break every mod in the pack that has nothing to do with history,
     * and it would be a guess. The same rule as step 45's weapon gate and step 46's vehicle
     * resolver: <i>never hand back nothing, and never pretend a guess is knowledge.</i>
     */
    public Verdict judge(@Nullable String itemId, int year, Setting setting) {
        if (itemId == null || itemId.isBlank()) {
            return new Verdict(String.valueOf(itemId), Kind.REAL, Action.ALLOW, true, null,
                    "nothing to judge");
        }
        Row r = rows.get(itemId);
        if (r == null) {
            return new Verdict(itemId, Kind.REAL, Action.ALLOW, true, null,
                    "not in the register, and an unknown object is never refused");
        }
        switch (r.kind()) {
            case REAL, MYTH -> {
                boolean yet = r.year() != null && year >= r.year();
                if (!yet) {
                    return new Verdict(itemId, r.kind(), r.action(), false, null,
                            "it arrives in " + r.year() + " and it is " + year);
                }
                boolean rename = r.action() == Action.RENAME;
                return new Verdict(itemId, r.kind(), r.action(), true,
                        rename ? r.asWhat() : null,
                        rename ? "here as " + r.asWhat() + " since " + r.year()
                               : "real here since " + r.year());
            }
            case NEVER -> {
                // THE ONE PLACE THE SETTING MATTERS.
                if (setting == Setting.TRAVELLER) {
                    return new Verdict(itemId, Kind.NEVER, Action.ALLOW, true, null,
                            "a time traveller may carry it, and will be seen doing it");
                }
                return new Verdict(itemId, Kind.NEVER, Action.LOCK, false, null,
                        "nothing like it has existed at any date");
            }
            default -> {
                return new Verdict(itemId, Kind.REAL, Action.ALLOW, true, null, "unknown kind");
            }
        }
    }

    /** What to call an object here and now: its historical name when it is renamed, else the id. */
    public String nameFor(String itemId, int year, Setting setting) {
        Verdict v = judge(itemId, year, setting);
        return v.renamed() ? v.asWhat() : itemId;
    }

    /** Every object the register locks for this year and setting. */
    public List<String> lockedAt(int year, Setting setting) {
        List<String> out = new ArrayList<>();
        for (Row r : rows.values()) if (!judge(r.id(), year, setting).allowed()) out.add(r.id());
        return out;
    }

    /** The objects that are renamed here and now, as {@code id -> the name it takes}. */
    public Map<String, String> renamedAt(int year, Setting setting) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Row r : rows.values()) {
            Verdict v = judge(r.id(), year, setting);
            if (v.renamed()) out.put(r.id(), v.asWhat());
        }
        return out;
    }

    public int count(Kind kind) {
        int n = 0;
        for (Row r : rows.values()) if (r.kind() == kind) n++;
        return n;
    }

    /** One line for {@code /ee check}. */
    public String summary(Setting setting) {
        if (!hasData()) return "the anachronism register has no data: " + problem;
        return rows.size() + " rows: " + count(Kind.REAL) + " real, " + count(Kind.MYTH)
                + " myth, " + count(Kind.NEVER) + " never; setting " + setting.name().toLowerCase(
                        Locale.ROOT);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        return "Anachronisms[" + rows.size() + ", " + (problem == null ? RESOURCE : problem) + "]";
    }
}
