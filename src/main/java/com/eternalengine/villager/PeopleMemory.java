package com.eternalengine.villager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The people a place remembers.
 *
 * <h3>Why this exists</h3>
 * A person who is removed from the world used to be gone for good: the entity was discarded, and
 * nothing kept its name, its face or its role. Spawn somebody again and it was a stranger — the same
 * square, a different person, every visit. That is the note this class answers: <i>"that npc can be
 * removed but it had to remember and spawn an exact replica npc again with same skin, name, etc."</i>
 *
 * <h3>What it holds</h3>
 * Everything the game needs to build the identical person again, and nothing else:
 * <ul>
 *   <li>the <b>UUID</b> — the client picks the skin from it, so the same UUID is the same face;</li>
 *   <li>the <b>name</b>, the <b>civilization</b>, the <b>role</b>, the <b>era</b> and the
 *       <b>gender</b> — the rest of what a person is to the game;</li>
 *   <li>where they were last seen, so they come back to their own place and not to somebody
 *       else's.</li>
 * </ul>
 *
 * <h3>Where the memory lives</h3>
 * With the world, as {@code SavedData}: written when the world is saved, read back when it is
 * loaded. A person remembered before a restart is the same person after it — which an in-memory
 * map could never promise.
 *
 * <h3>What it is not</h3>
 * This is not a second population. It holds only people who are <em>not standing anywhere</em> —
 * somebody is taken out of the memory the moment they are put back in the world — so its size is
 * bounded by the people a place has lost, capped per place, and never grows while the world is
 * being played.
 */
public final class PeopleMemory extends SavedData {

    /** How many people one place remembers. A district holds far fewer than this at a time. */
    public static final int PER_PLACE_CAP = 64;

    /** The name this store is saved under. */
    public static final String STORE_NAME = "eternal_people";

    private final Map<String, Deque<VirtualVillager>> byPlace = new HashMap<>();

    /** An empty memory, for a world that has none yet — or for a check that wants to start clean. */
    public PeopleMemory() {
    }

    /** The memory of a world, loaded from its save the first time it is asked for. */
    public static PeopleMemory get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PeopleMemory::load, PeopleMemory::new, STORE_NAME);
    }

    /**
     * Which place a point belongs to: the 128-block square of ground it stands in.
     *
     * <p>A place is ground, and ground does not move. Two districts whose centres land in the same
     * square are the same place to this memory, and somebody remembered at a market stall comes back
     * to that market and not to the next town.
     */
    public static String placeKey(int x, int z) {
        return Math.floorDiv(x, 128) + "," + Math.floorDiv(z, 128);
    }

    /** Remember a person who is no longer standing. */
    public void remember(String place, VirtualVillager person) {
        if (place == null || person == null) {
            return;
        }
        Deque<VirtualVillager> held = byPlace.computeIfAbsent(place, k -> new ArrayDeque<>());
        // The same person is never remembered twice: two records of one UUID would let the same
        // person be put in the world at the same time as themselves.
        for (VirtualVillager other : held) {
            if (other.entityUuid().equals(person.entityUuid())) {
                return;
            }
        }
        held.addFirst(person);
        while (held.size() > PER_PLACE_CAP) {
            held.removeLast();
        }
        setDirty();
    }

    /**
     * Take a person back out of the memory, ready to be put in the world again.
     *
     * <p>A person of the role that is wanted is preferred, so a market that needs a smith gets a
     * smith back if it remembers one; anybody remembered is better than somebody invented.
     *
     * @return the person, or null when this place remembers nobody who can fill the role
     */
    public VirtualVillager recall(String place, String roleType) {
        Deque<VirtualVillager> held = place == null ? null : byPlace.get(place);
        if (held == null || held.isEmpty()) {
            return null;
        }
        VirtualVillager chosen = null;
        if (roleType != null) {
            for (VirtualVillager person : held) {
                if (roleType.equalsIgnoreCase(person.roleType())) {
                    chosen = person;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = held.peekFirst();
        }
        held.remove(chosen);
        if (held.isEmpty()) {
            byPlace.remove(place);
        }
        setDirty();
        return chosen;
    }

    /**
     * Step 25: like {@link #recall(String, String)}, but only a person who {@code fits} (the same people and about the
     * same time as the place now); the others stay remembered for when the place is theirs again.
     *
     * @return the person, or null when this place remembers nobody who fits
     */
    public VirtualVillager recall(String place, String roleType, java.util.function.Predicate<VirtualVillager> fits) {
        Deque<VirtualVillager> held = place == null ? null : byPlace.get(place);
        if (held == null || held.isEmpty()) {
            return null;
        }
        VirtualVillager chosen = null;
        VirtualVillager anyRole = null;
        for (VirtualVillager person : held) {
            if (!fits.test(person)) {
                continue;
            }
            if (anyRole == null) {
                anyRole = person;
            }
            if (roleType != null && roleType.equalsIgnoreCase(person.roleType())) {
                chosen = person;
                break;
            }
        }
        if (chosen == null) {
            chosen = anyRole;
        }
        if (chosen == null) {
            return null;
        }
        held.remove(chosen);
        if (held.isEmpty()) {
            byPlace.remove(place);
        }
        setDirty();
        return chosen;
    }

    /** Step 21: whether any place remembers this person (for the checks and the sandbox tests). */
    public boolean remembers(java.util.UUID person) {
        if (person == null) {
            return false;
        }
        for (Deque<VirtualVillager> held : byPlace.values()) {
            for (VirtualVillager v : held) {
                if (person.equals(v.entityUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many people this place remembers. */
    public int remembered(String place) {
        Deque<VirtualVillager> held = byPlace.get(place);
        return held == null ? 0 : held.size();
    }

    /** How many people every place remembers together. */
    public int size() {
        int total = 0;
        for (Deque<VirtualVillager> held : byPlace.values()) {
            total += held.size();
        }
        return total;
    }

    /**
     * Everyone this memory holds, in no particular order — for the checks that have to look at every
     * person at once rather than one place at a time (board 17: nobody may stand after their own
     * civilization has fallen).
     */
    public List<VirtualVillager> everyone() {
        List<VirtualVillager> out = new ArrayList<>();
        for (Deque<VirtualVillager> held : byPlace.values()) {
            out.addAll(held);
        }
        return out;
    }

    /** Every place this memory knows about, for a command or a log line. */
    public List<String> places() {
        return new ArrayList<>(byPlace.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag places = new ListTag();
        for (Map.Entry<String, Deque<VirtualVillager>> entry : byPlace.entrySet()) {
            CompoundTag place = new CompoundTag();
            place.putString("Place", entry.getKey());
            ListTag people = new ListTag();
            for (VirtualVillager person : entry.getValue()) {
                people.add(person.save());
            }
            place.put("People", people);
            places.add(place);
        }
        tag.put("Places", places);
        return tag;
    }

    /** Read a memory back out of a save. */
    public static PeopleMemory load(CompoundTag tag) {
        PeopleMemory memory = new PeopleMemory();
        ListTag places = tag.getList("Places", 10);
        for (int i = 0; i < places.size(); i++) {
            CompoundTag place = places.getCompound(i);
            String key = place.getString("Place");
            ListTag people = place.getList("People", 10);
            for (int k = 0; k < people.size(); k++) {
                memory.remember(key, VirtualVillager.load(people.getCompound(k)));
            }
        }
        return memory;
    }
}
