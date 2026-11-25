package com.eternalengine.villager;

import com.eternalengine.civilization.Reactions;
import com.eternalengine.civilization.SocialOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step 42 (D18, friendship and greetings): <b>what a place thinks of you</b>.
 *
 * <h3>Why this exists</h3>
 *
 * <p>D18 asks that people <i>"remember you, greeting you by name and keeping an opinion of each
 * player, and that players can form friendships"</i>. Before this class the mod had an opinion of a
 * player in exactly one place: {@code ReputationEngine}, which keeps 8 positive and 13 negative
 * dimensions <b>with no place and no year</b>. One global number per player, so a village you have
 * never visited feels about you exactly as your home village does, and a man in 3000 BC feels
 * exactly as a man in 1900. And nobody greeted you at all: {@code PeopleMemory} remembers people a
 * place has <em>lost</em>, and holds no opinion of anybody.
 *
 * <p>This is the other half. It is keyed by <b>place and player</b>, the same 128-block square
 * {@code PeopleMemory} uses, so the two agree about where "here" is:
 *
 * <ul>
 *   <li>a <b>standing</b> from -100 to 100, 0 meaning a stranger;</li>
 *   <li>how many times you have been seen here, and <b>the year you were last seen</b> — the
 *       shared timeline, so "it has been a while" is a number and not a guess;</li>
 *   <li>your <b>name as they know it</b>, so the greeting survives a restart.</li>
 * </ul>
 *
 * <h3>Where it lives</h3>
 *
 * <p>With the world, as {@code SavedData}, written when the world saves and read back when it
 * loads. An opinion earned before a restart is the same opinion after it.
 *
 * <h3>What it holds back</h3>
 *
 * <p>The <b>words</b> are not here. They are in {@link Reactions}, which needs no Minecraft and is
 * therefore the code the probe runs; this class decides the number and asks for the line. That
 * split is the one step 40 set down with {@code ArmyChecks} and {@code HistoryChecks}.
 *
 * <p>Friendship is not a separate flag. It is a threshold on the standing, because a place that
 * thinks well of you <b>is</b> your friend's place, and a second number would only ever disagree
 * with the first.
 */
public final class PlayerStanding extends SavedData {

    /** How many players one place holds an opinion of. Older names fall off the end. */
    public static final int PER_PLACE_CAP = 64;

    /** The name this store is saved under. */
    public static final String STORE_NAME = "eternal_standing";

    /** At or above this, a place counts you a friend. */
    public static final int FRIEND_AT = 60;

    /** At or below this, a place would rather you were not here. */
    public static final int UNWELCOME_AT = -60;

    /**
     * What one place thinks of one player.
     *
     * @param standing -100 to 100, 0 a stranger
     * @param visits   how many times he has been seen here
     * @param lastYear the year of the last visit, on the shared timeline
     * @param name     his name as this place knows it
     */
    public record Opinion(int standing, int visits, int lastYear, String name) {

        public boolean isFriend() {
            return standing >= FRIEND_AT;
        }

        public boolean isUnwelcome() {
            return standing <= UNWELCOME_AT;
        }

        public boolean isStranger() {
            return visits == 0 && standing == 0;
        }
    }

    /** place -> (player -> opinion), in the order players were first seen. */
    private final Map<String, LinkedHashMap<UUID, Opinion>> byPlace = new HashMap<>();

    public PlayerStanding() {}

    /** The standing of a world, loaded from its save the first time it is asked for. */
    public static PlayerStanding get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlayerStanding::load, PlayerStanding::new, STORE_NAME);
    }

    /** Which place a point belongs to: the same square {@code PeopleMemory} uses. */
    public static String placeKey(int x, int z) {
        return PeopleMemory.placeKey(x, z);
    }

    // ------------------------------------------------------------------ reading

    /** What this place thinks of this player; a stranger when it has never seen him. */
    public Opinion opinion(String place, UUID player) {
        if (place == null || player == null) {
            return new Opinion(0, 0, 0, null);
        }
        LinkedHashMap<UUID, Opinion> here = byPlace.get(place);
        Opinion held = here == null ? null : here.get(player);
        return held == null ? new Opinion(0, 0, 0, null) : held;
    }

    /** Just the number, for code that sums or compares and does not speak. */
    public int standing(String place, UUID player) {
        return opinion(place, player).standing();
    }

    public boolean isFriend(String place, UUID player) {
        return opinion(place, player).isFriend();
    }

    public boolean isUnwelcome(String place, UUID player) {
        return opinion(place, player).isUnwelcome();
    }

    /** How warm it is, as a {@link Reactions} number. */
    public int warmth(String place, UUID player) {
        return Reactions.warmth(opinion(place, player).standing());
    }

    /** How many places hold any opinion of this player at all. */
    public int placesKnown(UUID player) {
        if (player == null) {
            return 0;
        }
        int n = 0;
        for (LinkedHashMap<UUID, Opinion> here : byPlace.values()) {
            if (here.containsKey(player)) {
                n++;
            }
        }
        return n;
    }

    /** How many players this place holds an opinion of. */
    public int known(String place) {
        LinkedHashMap<UUID, Opinion> here = place == null ? null : byPlace.get(place);
        return here == null ? 0 : here.size();
    }

    /** Every place this store knows about, for a command or a log line. */
    public List<String> places() {
        return new ArrayList<>(byPlace.keySet());
    }

    /** How many opinions are held in total, for the checks. */
    public int size() {
        int total = 0;
        for (LinkedHashMap<UUID, Opinion> here : byPlace.values()) {
            total += here.size();
        }
        return total;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Move what a place thinks of a player, and set it dirty.
     *
     * <p>Clamped to -100..100, because a place that already hates you cannot hate you more, and the
     * same in the other direction.
     */
    public int add(String place, UUID player, int delta) {
        if (place == null || player == null || delta == 0) {
            return opinion(place, player).standing();
        }
        Opinion was = opinion(place, player);
        int now = Math.max(-100, Math.min(100, was.standing() + delta));
        put(place, player, new Opinion(now, was.visits(), was.lastYear(), was.name()));
        return now;
    }

    /**
     * A visit. Raises the count, records the year on the shared timeline, and remembers the name.
     *
     * <p>The year is stored and not computed later: a place remembers when it last saw you, and
     * that is a fact about the past and not a subtraction.
     */
    public void visit(String place, UUID player, String name, int year) {
        if (place == null || player == null) {
            return;
        }
        Opinion was = opinion(place, player);
        put(place, player, new Opinion(was.standing(), was.visits() + 1, year,
                name == null || name.isBlank() ? was.name() : name));
    }

    /** Wipe the slate: this place has never seen this player. */
    public void forget(String place, UUID player) {
        if (place == null || player == null) {
            return;
        }
        LinkedHashMap<UUID, Opinion> here = byPlace.get(place);
        if (here == null) {
            return;
        }
        here.remove(player);
        if (here.isEmpty()) {
            byPlace.remove(place);
        }
        setDirty();
    }

    /** Wipe every opinion of this player everywhere, for a command or a fresh start. */
    public void forget(UUID player) {
        if (player == null) {
            return;
        }
        boolean changed = false;
        for (String place : new ArrayList<>(byPlace.keySet())) {
            LinkedHashMap<UUID, Opinion> here = byPlace.get(place);
            if (here != null && here.remove(player) != null) {
                changed = true;
                if (here.isEmpty()) {
                    byPlace.remove(place);
                }
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private void put(String place, UUID player, Opinion opinion) {
        LinkedHashMap<UUID, Opinion> here = byPlace.computeIfAbsent(place, k -> new LinkedHashMap<>());
        here.remove(player);                       // re-insert, so the order records "seen last"
        here.put(player, opinion);
        while (here.size() > PER_PLACE_CAP) {
            UUID oldest = here.keySet().iterator().next();
            here.remove(oldest);
        }
        setDirty();
    }

    // ------------------------------------------------------------------ the words

    /**
     * D18: what a person here says when he sees you.
     *
     * <p>The number comes from this store and the words from {@link Reactions}, which reads the
     * social order of the place and the year (D7) to decide whether he may open his mouth at all.
     *
     * @param greeter the order of the man speaking, or null when nobody in particular is
     * @param rank    the order you are taken to stand in, or null when that is not known
     */
    public String greeting(String place, UUID player, SocialOrder.Stratum greeter,
                           SocialOrder.Stratum rank, double lat, double lon, int year) {
        Opinion o = opinion(place, player);
        return Reactions.greet(o.standing(), o.name(), greeter, rank, lat, lon, year);
    }

    /** The same, with no particular man speaking and your rank here unknown. */
    public String greeting(String place, UUID player, double lat, double lon, int year) {
        return greeting(place, player, null, null, lat, lon, year);
    }

    /** D13: how the people of this place react to you, in the words of this place. */
    public String reaction(String place, UUID player, double lat, double lon, int year) {
        return Reactions.toPlayer(opinion(place, player).standing(), lat, lon, year);
    }

    // ------------------------------------------------------------------ the file

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag places = new ListTag();
        for (Map.Entry<String, LinkedHashMap<UUID, Opinion>> e : byPlace.entrySet()) {
            CompoundTag place = new CompoundTag();
            place.putString("Place", e.getKey());
            ListTag players = new ListTag();
            for (Map.Entry<UUID, Opinion> p : e.getValue().entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("Player", p.getKey());
                Opinion o = p.getValue();
                one.putInt("Standing", o.standing());
                one.putInt("Visits", o.visits());
                one.putInt("LastYear", o.lastYear());
                if (o.name() != null) {
                    one.putString("Name", o.name());
                }
                players.add(one);
            }
            place.put("Players", players);
            places.add(place);
        }
        tag.put("Places", places);
        return tag;
    }

    /** Read a world's standing back out of its save. */
    public static PlayerStanding load(CompoundTag tag) {
        PlayerStanding store = new PlayerStanding();
        ListTag places = tag.getList("Places", 10);
        for (int i = 0; i < places.size(); i++) {
            CompoundTag place = places.getCompound(i);
            String key = place.getString("Place");
            ListTag players = place.getList("Players", 10);
            for (int k = 0; k < players.size(); k++) {
                CompoundTag one = players.getCompound(k);
                String name = one.contains("Name") ? one.getString("Name") : null;
                store.put(key, one.getUUID("Player"),
                        new Opinion(one.getInt("Standing"), one.getInt("Visits"),
                                one.getInt("LastYear"), name));
            }
        }
        return store;
    }
}
