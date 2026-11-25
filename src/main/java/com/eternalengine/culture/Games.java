package com.eternalengine.culture;

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
 * Step 47 part 6 (E17): <b>games and sports</b>.
 *
 * <p>The plan names chess, polo, cricket, kites, theatre and cinema, and the survey said "not yet".
 * That was right about the code and wrong about the data: the products atlas already carries
 * {@code chess}, {@code dice}, {@code backgammon}, {@code kites}, {@code playing_cards},
 * {@code theatre} and {@code fireworks} with a year for all 17 areas. What nothing carried is the
 * <b>game as a thing people play</b>: where it came from, who plays it, and whether a crowd is part
 * of it.
 *
 * <h2>The finding</h2>
 *
 * <p><b>A game is the cheapest thing a culture exports and the last thing it gives up.</b> Board
 * games travel with merchants and cross every border within a few centuries. Sports travel with
 * empires and then with schools, and <b>the codified ones are astonishingly late</b>: football is a
 * folk game in every European village and a <i>rule</i> in 1863, basketball is 1891, the modern
 * Olympics are 1896. Before the nineteenth century a crowd does not watch a sport, it watches a
 * <b>spectacle</b>, and the Mesoamerican ball game is the extreme case: a religious obligation with
 * a ballcourt in the middle of the city and, at the end of some of them, a death.
 *
 * <h2>The refusal, which is the design of this register</h2>
 *
 * <p>A crop has an arrival in every area because the atlas computed one from a technology and a
 * parent. Nobody can say in what year polo reached Java, so a game here has an <b>origin</b> and only
 * the spread years <b>the record gives</b>. Everywhere else the answer is {@link #UNKNOWN}, and
 * {@code UNKNOWN} is not {@code NEVER}: it means this register did not look and will not guess. A
 * game register that invents an arrival for kabaddi in Peru is worse than one that says it does not
 * know.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Games {

    public static final String RESOURCE = "/data/eternalengine/eternal/names/games.json.gz";

    /** Not recorded here. Deliberately not the same as "never played there". */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public record Game(String id, String name, String kind, int year, String home, String players,
                       @Nullable String equipment, Map<String, Integer> spread, String note,
                       String source) {

        /** The year the game is recorded in an area, or {@link #UNKNOWN}. */
        public int since(String area) {
            Integer y = spread.get(area);
            return y == null ? UNKNOWN : y;
        }

        /** True only if the record puts it there by then. An unknown is not a yes. */
        public boolean recordedIn(String area, int y) {
            int s = since(area);
            return s != UNKNOWN && y >= s;
        }

        public boolean fromHome(String area) {
            return home.equals(area);
        }

        /** A game a crowd watches is not the same town as a game two people play. */
        public boolean spectacle() {
            return "watched".equals(players);
        }
    }

    private static volatile Games instance;

    private final List<Game> games;
    private final Map<String, Game> byId;
    private final Map<String, Game> byEquipment;
    private final List<String> areas;

    private Games(JsonObject root) {
        List<Game> list = new ArrayList<>();
        Map<String, Game> byEq = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("games")) {
            JsonObject o = e.getAsJsonObject();
            Game g = new Game(str(o, "id"), str(o, "name"), str(o, "kind"),
                    o.get("year").getAsInt(), str(o, "home"), str(o, "players"),
                    o.has("equipment") && !o.get("equipment").isJsonNull()
                            ? o.get("equipment").getAsString() : null,
                    ints(o, "spread"), str(o, "note"), str(o, "source"));
            list.add(g);
            if (g.equipment() != null) byEq.put(g.equipment(), g);
        }
        this.games = List.copyOf(list);
        Map<String, Game> m = new LinkedHashMap<>();
        for (Game g : list) {
            m.put(g.id(), g);
        }
        this.byId = Map.copyOf(m);
        this.byEquipment = Map.copyOf(byEq);
        this.areas = strings(root.getAsJsonArray("areas"));
    }

    public static Games get() {
        Games g = instance;
        if (g == null) {
            synchronized (Games.class) {
                g = instance;
                if (g == null) {
                    g = load();
                    instance = g;
                }
            }
        }
        return g;
    }

    private static Games load() {
        try (InputStream raw = Games.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Games(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Game> games() {
        return games;
    }

    public List<String> areas() {
        return areas;
    }

    @Nullable
    public Game game(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** The game a Minecraft item stands for, where one does (chess, kites, cards, dice). */
    @Nullable
    public Game forEquipment(String item) {
        return byEquipment.get(item.toLowerCase(Locale.ROOT));
    }

    /** The games recorded in an area in a year, oldest first. */
    public List<String> playableIn(String area, int year) {
        List<Game> out = new ArrayList<>();
        for (Game g : games) {
            if (g.recordedIn(area, year)) out.add(g);
        }
        out.sort((a, b) -> Integer.compare(a.year(), b.year()));
        List<String> ids = new ArrayList<>();
        for (Game g : out) ids.add(g.id());
        return ids;
    }

    /** The one line to show a player, which says where the game came from. */
    public String why(String id, String area, int year) {
        Game g = game(id);
        if (g == null) return id + ": not in the register";
        int s = g.since(area);
        if (s == UNKNOWN) {
            return g.name() + ": from " + g.home() + " in " + g.year()
                    + ", and this register does not record when it reached " + area;
        }
        if (year < s) return g.name() + ": from " + g.home() + ", not in " + area + " until " + s;
        return g.name() + ": from " + g.home() + ", played here since " + s;
    }

    public int count() {
        return games.size();
    }

    public String summary() {
        return games.size() + " games over " + areas.size() + " areas; "
                + games.stream().filter(Game::spectacle).count() + " of them watched by a crowd";
    }

    // ---------------------------------------------------------------------------------------
    private static Map<String, Integer> ints(JsonObject o, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) return Map.copyOf(out);
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            if (x.getValue().isJsonNull()) continue;
            out.put(x.getKey(), x.getValue().getAsInt());
        }
        return Map.copyOf(out);
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return List.copyOf(out);
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
