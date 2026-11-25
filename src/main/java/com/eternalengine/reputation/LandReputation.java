package com.eternalengine.reputation;

import com.eternalengine.civilization.CountryGrid;
import com.eternalengine.war.Sides;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step 42 (F2, reputation per land): <b>the deeds a world remembers, and the oaths sworn in it</b>.
 *
 * <p>This is the store; {@link Fame} is the arithmetic and every number here is asked of it. The
 * split is the one step 40 set down with {@code ArmyChecks} and the one part 4 of this step used
 * for {@code Reactions}: the part with the maths in it imports no Minecraft, so the probe runs the
 * very code the game runs.
 *
 * <h3>What is stored</h3>
 *
 * <ul>
 *   <li><b>Deeds</b> — what was done, where, when and what it was worth, capped per player. A
 *       land's opinion is <i>derived</i> from these and never written down, so a deed done in
 *       Bordeaux in 1400 is unknown in Constantinople that year, known there later and weaker, and
 *       forgotten in Bordeaux by about 1500.</li>
 *   <li><b>Oaths</b> — loyalty to a {@code Sides.Party}: a house, a city, a faith, a state, a hired
 *       company. Keyed by party and not by land, because you swear to a <i>body</i> and not to a
 *       field, and the body ends while the field stays.</li>
 * </ul>
 *
 * <h3>Why this and not the reputation engine</h3>
 *
 * <p>{@code ReputationEngine} is <b>kept</b>, exactly as the survey decided. Its 21 dimensions
 * answer a different question — <i>what kind of man are you</i> — and a man's cruelty is his
 * cruelty everywhere, so that number is rightly global and is left alone. This answers <i>what do
 * they think of you here</i>, which is the question F2 asks, and which until now had no answer at
 * any scale.
 *
 * <p>The capability's {@code regionalRep} and {@code factionRep} slots, built long ago and never
 * written to by anything, are the obvious home for this — and they are still the right place for the
 * number the <i>client</i> needs. {@link #mirror} fills them, so the reputation packet already on
 * the wire carries the land's opinion without a new packet being invented.
 */
public final class LandReputation extends SavedData {

    /** How many deeds one player's name carries. Older ones fall off the end. */
    public static final int DEED_CAP = 64;

    /** The name this store is saved under. */
    public static final String STORE_NAME = "eternal_fame";

    /** An oath: to whom, how strongly, and when it was sworn. */
    public record Oath(String party, int value, int year) {}

    private final Map<UUID, Deque<Fame.Deed>> deeds = new HashMap<>();
    private final Map<UUID, LinkedHashMap<String, Oath>> oaths = new HashMap<>();

    public LandReputation() {}

    /** The fame of a world, loaded from its save the first time it is asked for. */
    public static LandReputation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LandReputation::load, LandReputation::new, STORE_NAME);
    }

    // ------------------------------------------------------------------ deeds

    /** The deeds this player's name carries, newest first. */
    public List<Fame.Deed> deeds(UUID player) {
        Deque<Fame.Deed> held = player == null ? null : deeds.get(player);
        return held == null ? List.of() : new ArrayList<>(held);
    }

    /**
     * Write a deed into the world.
     *
     * <p>Nothing is computed here: the deed is kept, and every opinion is worked out from it when
     * it is asked for. That is what lets the same deed be unknown in one place and notorious in
     * another in the same year.
     */
    public void record(UUID player, String what, int delta, double lat, double lon, int year) {
        if (player == null || delta == 0) {
            return;
        }
        Deque<Fame.Deed> held = deeds.computeIfAbsent(player, k -> new ArrayDeque<>());
        held.addFirst(new Fame.Deed(what, year, lat, lon, Fame.clamp(delta)));
        while (held.size() > DEED_CAP) {
            held.removeLast();
        }
        setDirty();
    }

    /** What they think of you at this point, in this year. */
    public int opinion(UUID player, double lat, double lon, int year) {
        return Fame.opinion(deeds(player), lat, lon, year);
    }

    /** What a whole land thinks of you, asked at the middle of it. */
    public int landOpinion(UUID player, String landCode, int year) {
        return Fame.landOpinion(deeds(player), landCode, year);
    }

    /** The land you are standing in, which is the one most commands mean. */
    public String landAt(double lat, double lon) {
        return CountryGrid.get().codeAt(lat, lon);
    }

    /** Every land that has any opinion of you, strongest first. */
    public List<String> lands(UUID player, int year) {
        return Fame.lands(deeds(player), year);
    }

    /** The words for the number here. */
    public String phrase(UUID player, double lat, double lon, int year) {
        return Fame.phrase(opinion(player, lat, lon, year));
    }

    // ------------------------------------------------------------------ oaths

    /**
     * Swear, strengthen or break an oath to a party.
     *
     * <p>The party has to be one {@code Sides} knows: you can be loyal to a house, a commune, a
     * faith, a state or a company, and this will not invent a body to be loyal to.
     */
    public Oath swear(UUID player, String partyId, int delta, int year) {
        if (player == null || partyId == null || Sides.get().party(partyId) == null) {
            return null;
        }
        LinkedHashMap<String, Oath> held = oaths.computeIfAbsent(player, k -> new LinkedHashMap<>());
        Oath was = held.get(partyId);
        int value = Fame.clamp((was == null ? 0 : was.value()) + delta);
        int sworn = was == null ? year : was.year();          // the year the oath was first sworn
        Oath now = new Oath(partyId, value, sworn);
        held.put(partyId, now);
        setDirty();
        return now;
    }

    /**
     * How loyal to this party you are, in this year.
     *
     * <p>Decayed at {@link Fame#OATH_HALF_LIFE_YEARS}, slower than a name, because a tale needs a
     * witness and a promise does not. A party no longer in force keeps the number — a man can be
     * loyal to a house that has fallen — but {@link #swornLive} says whether anyone can call on it.
     */
    public int loyalty(UUID player, String partyId, int year) {
        if (player == null || partyId == null) {
            return 0;
        }
        Map<String, Oath> held = oaths.get(player);
        Oath o = held == null ? null : held.get(partyId);
        return o == null ? 0 : Fame.decay(o.value(), o.year(), year, Fame.OATH_HALF_LIFE_YEARS);
    }

    /** Whether the oath can still be called on: is the party still in force this year? */
    public boolean swornLive(String partyId, int year) {
        Sides.Party p = Sides.get().party(partyId);
        return p != null && p.inForce(year);
    }

    /** Every oath this player has sworn, strongest first. */
    public List<Oath> oaths(UUID player, int year) {
        Map<String, Oath> held = player == null ? null : oaths.get(player);
        if (held == null) {
            return List.of();
        }
        List<Oath> out = new ArrayList<>(held.values());
        out.sort((a, b) -> Integer.compare(
                Math.abs(Fame.decay(b.value(), b.year(), year, Fame.OATH_HALF_LIFE_YEARS)),
                Math.abs(Fame.decay(a.value(), a.year(), year, Fame.OATH_HALF_LIFE_YEARS))));
        return out;
    }

    /** The party you are most bound to this year, or null. */
    public Oath strongestOath(UUID player, int year) {
        List<Oath> held = oaths(player, year);
        return held.isEmpty() ? null : held.get(0);
    }

    // ------------------------------------------------------------------ housekeeping

    /** Wipe a player's name from the world: his deeds and his oaths both. */
    public void forget(UUID player) {
        if (player == null) {
            return;
        }
        boolean changed = deeds.remove(player) != null || oaths.remove(player) != null;
        if (changed) {
            setDirty();
        }
    }

    /** How much this store holds, for the checks. */
    public int size() {
        int total = 0;
        for (Deque<Fame.Deed> held : deeds.values()) {
            total += held.size();
        }
        for (Map<String, Oath> held : oaths.values()) {
            total += held.size();
        }
        return total;
    }

    /** How many players this store holds anything about: deeds or oaths, counted once. */
    public int playerCount() {
        java.util.Set<UUID> all = new java.util.HashSet<>(deeds.keySet());
        all.addAll(oaths.keySet());
        return all.size();
    }

    /**
     * Copy this player's standing into the capability's own slots, so the reputation packet already
     * on the wire carries it.
     *
     * <p>The capability's {@code regionalRep} and {@code factionRep} maps were built for exactly
     * this and have never been written to by anything. Filling them costs nothing and means the
     * client learns the land's opinion without a new packet.
     */
    public void mirror(com.eternalengine.capability.IPlayerSimulationData data,
                       UUID player, double lat, double lon, int year) {
        if (data == null || player == null) {
            return;
        }
        data.setRegionalRep(landAt(lat, lon), opinion(player, lat, lon, year));
        for (String land : lands(player, year)) {
            data.setRegionalRep(land, landOpinion(player, land, year));
        }
        for (Oath o : oaths(player, year)) {
            data.setFactionRep(o.party(), loyalty(player, o.party(), year));
        }
    }

    // ------------------------------------------------------------------ the file

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Deque<Fame.Deed>> e : deeds.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Player", e.getKey());
            ListTag list = new ListTag();
            for (Fame.Deed d : e.getValue()) {
                CompoundTag dt = new CompoundTag();
                dt.putString("What", d.what() == null ? "" : d.what());
                dt.putInt("Year", d.year());
                dt.putDouble("Lat", d.lat());
                dt.putDouble("Lon", d.lon());
                dt.putInt("Delta", d.delta());
                list.add(dt);
            }
            one.put("Deeds", list);
            players.add(one);
        }
        tag.put("Deeds", players);

        ListTag sworn = new ListTag();
        for (Map.Entry<UUID, LinkedHashMap<String, Oath>> e : oaths.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Player", e.getKey());
            ListTag list = new ListTag();
            for (Oath o : e.getValue().values()) {
                CompoundTag ot = new CompoundTag();
                ot.putString("Party", o.party());
                ot.putInt("Value", o.value());
                ot.putInt("Year", o.year());
                list.add(ot);
            }
            one.put("Oaths", list);
            sworn.add(one);
        }
        tag.put("Oaths", sworn);
        return tag;
    }

    /** Read a world's fame back out of its save. */
    public static LandReputation load(CompoundTag tag) {
        LandReputation store = new LandReputation();
        ListTag players = tag.getList("Deeds", 10);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag one = players.getCompound(i);
            UUID who = one.getUUID("Player");
            ListTag list = one.getList("Deeds", 10);
            for (int k = list.size() - 1; k >= 0; k--) {      // oldest first, so the newest ends up first
                CompoundTag dt = list.getCompound(k);
                store.record(who, dt.getString("What"), dt.getInt("Delta"),
                        dt.getDouble("Lat"), dt.getDouble("Lon"), dt.getInt("Year"));
            }
        }
        ListTag sworn = tag.getList("Oaths", 10);
        for (int i = 0; i < sworn.size(); i++) {
            CompoundTag one = sworn.getCompound(i);
            UUID who = one.getUUID("Player");
            ListTag list = one.getList("Oaths", 10);
            for (int k = 0; k < list.size(); k++) {
                CompoundTag ot = list.getCompound(k);
                store.swear(who, ot.getString("Party"), ot.getInt("Value"), ot.getInt("Year"));
            }
        }
        // A save that has just been read is not dirty: marking it so would make every load write.
        return store;
    }
}
