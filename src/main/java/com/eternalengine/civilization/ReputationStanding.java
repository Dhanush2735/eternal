package com.eternalengine.civilization;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the city holds about the player.
 *
 * <p>Two things live here, and they live at two different widths (the sheet's rule):
 *
 * <ul>
 *   <li><b>The two meters</b> — the positive and the negative, each on {@code -100 ... +100} — belong to
 *       the <b>player, city-wide</b>: every person in this city reads them. Good deeds move only the
 *       positive meter; bad ones only the negative one. Small marks fade as the days pass (a fifth of
 *       them goes, day by day). The tier a man reads is: <b>marked</b> (the negative at 30 or more — he
 *       does what he must, unwillingly), <b>trusted</b> (the positive at 30 or more), <b>wary</b> (the
 *       negative at 10 or more), <b>warm</b> (the positive at 10 or more), else <b>neutral</b>.
 *   <li><b>The remembering</b> — the meetings, what was last asked, the trade, the greeting — belongs to
 *       <b>each person separately</b>: talk to one man, and one man remembers you.
 * </ul>
 *
 * <p>The values are shown only on the debug screen — for now.
 */
public final class ReputationStanding extends SavedData {

    /** The name this store is saved under. */
    public static final String STORE_NAME = "eternal_standing";

    private final Map<String, Standing> byPlayer = new HashMap<>();

    /** The city's box for one player: the two meters, the name the player gave, and each man's memory of him. */
    public static final class Standing {
        /** The positive meter, on -100 ... +100. */
        public int posRep;
        /** The negative meter, on -100 ... +100. */
        public int negRep;
        /** The last day the standing was read; when the day advances, the small marks fade. */
        public int lastDay;
        /** The name the player gave, and the place he came from (the street knows both). */
        public String playerName = "";
        public String playerFrom = "";
        /** Each person of the city, and what that person remembers of the player. */
        public final Map<String, Person> persons = new HashMap<>();

        /** What one person of this city remembers about the player. */
        public static final class Person {
            /** How often this person has met this face. */
            public int meetings;
            /** The last day this person greeted him, and what was said. */
            public int greetedDay;
            public String greetText = "";
            /** The subject of the player's last question, and the day it was asked. */
            public String lastQ = "";
            public int lastQDay;
            /** Whether the two have traded since. */
            public boolean tradeUsed;
            /** Court talk pushed past this man's head (the bound line). */
            public int courtPush;
            /** The parry round-robin index. */
            public int par;
            /** The bond: register answers given (news, the ruler, the record). */
            public int bond;
            /** How much of the town's news has gone out in this talk, and on what day it began. */
            public int newsCount;
            public int newsDay;
        }
    }

    private ReputationStanding() {
    }

    /** The memory of a world, loaded from its save the first time it is asked for. */
    public static ReputationStanding get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ReputationStanding::load, ReputationStanding::new, STORE_NAME);
    }

    /**
     * The city's box for the player today. When the day has advanced since the last reading, the small
     * marks fade: a fifth of each meter goes, day by day, until only the big marks remain.
     */
    public Standing standing(UUID player, int today) {
        Standing st = byPlayer.computeIfAbsent(player.toString(), k -> new Standing());
        if (st.lastDay != 0 && st.lastDay < today) {
            st.posRep = Math.round(st.posRep * 0.75f);
            st.negRep = Math.round(st.negRep * 0.75f);
        }
        st.lastDay = today;
        setDirty();
        return st;
    }

    /**
     * What this person remembers of this player, today. Null when the person is unknown (test talks), in
     * which case there is simply nobody to remember.
     */
    @Nullable
    public Standing.Person person(UUID player, @Nullable UUID npc, int today) {
        if (npc == null) {
            return null;
        }
        Standing st = standing(player, today);
        return st.persons.computeIfAbsent(npc.toString(), k -> new Standing.Person());
    }

    /** A finished trade, noted: the positive meter moves, and the man remembers the cloth. */
    public void tradeDone(UUID player, @Nullable UUID npc, int today, int dv) {
        Standing st = standing(player, today);
        addPos(st, dv);
        if (npc != null) {
            st.persons.computeIfAbsent(npc.toString(), k -> new Standing.Person()).tradeUsed = true;
        }
        setDirty();
    }

    /** A good deed: the positive meter moves; the negative one does not. */
    public void addPos(Standing st, int dv) {
        st.posRep = clamp(st.posRep + dv);
        setDirty();
    }

    /** A bad deed: the negative meter moves; the positive one does not. */
    public void addNeg(Standing st, int dv) {
        st.negRep = clamp(st.negRep + dv);
        setDirty();
    }

    private static int clamp(int v) {
        return Math.max(-100, Math.min(100, v));
    }

    /** The tier every person in the city reads from the two meters. */
    public static String tier(int posRep, int negRep) {
        if (negRep >= 30) {
            return "marked";
        }
        if (posRep >= 30) {
            return "trusted";
        }
        if (negRep >= 10) {
            return "wary";
        }
        if (posRep >= 10) {
            return "warm";
        }
        return "neutral";
    }

    /** The mood's tail: how the tier reads in a man's face. */
    public static String tierSuffix(String tier) {
        return switch (tier) {
            case "trusted" -> " · trusts you";
            case "warm" -> " · warmer to you";
            case "wary" -> " · watchful of you";
            case "marked" -> " · wary of you";
            default -> "";
        };
    }

    /** A meter value as the debug screen shows it: signed. */
    public static String fmt(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    // ------------------------------------------------------------------ the save

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<String, Standing> entry : byPlayer.entrySet()) {
            Standing st = entry.getValue();
            CompoundTag p = new CompoundTag();
            p.putString("Player", entry.getKey());
            p.putInt("PosRep", st.posRep);
            p.putInt("NegRep", st.negRep);
            p.putInt("LastDay", st.lastDay);
            p.putString("PlayerName", st.playerName);
            p.putString("PlayerFrom", st.playerFrom);
            ListTag persons = new ListTag();
            for (Map.Entry<String, Standing.Person> pe : st.persons.entrySet()) {
                Standing.Person per = pe.getValue();
                CompoundTag np = new CompoundTag();
                np.putString("Npc", pe.getKey());
                np.putInt("Meetings", per.meetings);
                np.putInt("GreetedDay", per.greetedDay);
                np.putString("GreetText", per.greetText);
                np.putString("LastQ", per.lastQ);
                np.putInt("LastQDay", per.lastQDay);
                np.putBoolean("TradeUsed", per.tradeUsed);
                np.putInt("CourtPush", per.courtPush);
                np.putInt("Par", per.par);
                np.putInt("Bond", per.bond);
                np.putInt("NewsCount", per.newsCount);
                np.putInt("NewsDay", per.newsDay);
                persons.add(np);
            }
            p.put("Persons", persons);
            players.add(p);
        }
        tag.put("Players", players);
        return tag;
    }

    private static ReputationStanding load(CompoundTag tag) {
        ReputationStanding store = new ReputationStanding();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag p = players.getCompound(i);
            String id = p.getString("Player");
            if (id.isEmpty()) {
                continue;
            }
            Standing st = new Standing();
            st.posRep = clamp(p.getInt("PosRep"));
            st.negRep = clamp(p.getInt("NegRep"));
            st.lastDay = p.getInt("LastDay");
            st.playerName = p.getString("PlayerName");
            st.playerFrom = p.getString("PlayerFrom");
            ListTag persons = p.getList("Persons", Tag.TAG_COMPOUND);
            for (int j = 0; j < persons.size(); j++) {
                CompoundTag np = persons.getCompound(j);
                String npcId = np.getString("Npc");
                if (npcId.isEmpty()) {
                    continue;
                }
                Standing.Person per = new Standing.Person();
                per.meetings = np.getInt("Meetings");
                per.greetedDay = np.getInt("GreetedDay");
                per.greetText = np.getString("GreetText");
                per.lastQ = np.getString("LastQ");
                per.lastQDay = np.getInt("LastQDay");
                per.tradeUsed = np.getBoolean("TradeUsed");
                per.courtPush = np.getInt("CourtPush");
                per.par = np.getInt("Par");
                per.bond = np.getInt("Bond");
                per.newsCount = np.getInt("NewsCount");
                per.newsDay = np.getInt("NewsDay");
                st.persons.put(npcId, per);
            }
            store.byPlayer.put(id, st);
        }
        return store;
    }
}
