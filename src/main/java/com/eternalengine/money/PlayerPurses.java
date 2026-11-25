package com.eternalengine.money;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 19a: every player's purse, saved with the world. A purse is a number per kind of money (no coin items), kept
 * in that money's smallest unit: 192 pai, not "1 rupee". Money of another place or time stays in the purse as it
 * is; sellers there will not take it (the money-changer swaps it, step 19b).
 *
 * <p>The first time a purse is used, it gets a day's pay in the money of the place and year (the start money);
 * {@link #reset} empties a purse and lets the start money come again.
 */
public final class PlayerPurses extends SavedData {

    public static final String DATA_NAME = "eternalengine_purses";

    /** One player's purse (a copy when handed out). */
    public static final class Purse {
        final Map<String, Long> money = new LinkedHashMap<>();
        boolean started;
        int startDate;
        String startMoney = "";
        long startAmount;

        /** Money id to amount (smallest units), only kinds with something in them, in the order they came. */
        public Map<String, Long> money() {
            Map<String, Long> out = new LinkedHashMap<>();
            money.forEach((k, v) -> {
                if (v != null && v > 0) {
                    out.put(k, v);
                }
            });
            return out;
        }

        public long amount(String moneyId) {
            Long v = money.get(moneyId);
            return v == null ? 0L : v;
        }

        /** True once the start money was given. */
        public boolean started() { return started; }
        /** The day the start money was given (a date key), or 0. */
        public int startDate() { return startDate; }
        public String startMoney() { return startMoney; }
        public long startAmount() { return startAmount; }

        public boolean isEmpty() {
            return money().isEmpty();
        }

        Purse copy() {
            Purse p = new Purse();
            p.money.putAll(money);
            p.started = started;
            p.startDate = startDate;
            p.startMoney = startMoney;
            p.startAmount = startAmount;
            return p;
        }
    }

    private final Map<UUID, Purse> players = new ConcurrentHashMap<>();

    public static PlayerPurses get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerPurses::load, PlayerPurses::new, DATA_NAME);
    }

    /** A copy of this player's purse; an empty, unstarted one when they never had any. */
    public synchronized Purse view(UUID player) {
        Purse p = players.get(player);
        return p == null ? new Purse() : p.copy();
    }

    /**
     * Give the start money if this purse never had it: {@code amount} of {@code moneyId}, on day {@code date}.
     *
     * @return true when it was given now
     */
    public synchronized boolean startIfNew(UUID player, String moneyId, long amount, int date) {
        Purse p = players.computeIfAbsent(player, k -> new Purse());
        if (p.started) {
            return false;
        }
        p.started = true;
        p.startDate = date;
        p.startMoney = moneyId;
        p.startAmount = Math.max(0L, amount);
        if (amount > 0) {
            p.money.merge(moneyId, amount, Long::sum);
        }
        setDirty();
        return true;
    }

    /**
     * Put money in (delta above 0) or take it out (below 0).
     *
     * @return the new amount of that money, or -1 when there is not enough to take (nothing changes then)
     */
    public synchronized long add(UUID player, String moneyId, long delta) {
        Purse p = players.computeIfAbsent(player, k -> new Purse());
        long now = p.amount(moneyId);
        long next = now + delta;
        if (next < 0 || (delta > 0 && next < now) || next > MoneyFormat.MAX) {
            return -1L;
        }
        if (next == 0) {
            p.money.remove(moneyId);
        } else {
            p.money.put(moneyId, next);
        }
        setDirty();
        return next;
    }

    /**
     * Take {@code take} of one money out and put {@code give} of another in, both or neither (step 19b, the
     * money-changer).
     *
     * @return false, and nothing changes, when there is not enough to take or the purse cannot hold the new money
     */
    public synchronized boolean swap(UUID player, String fromId, long take, String toId, long give) {
        if (take <= 0 || give < 0 || fromId.equals(toId)) {
            return false;
        }
        Purse p = players.computeIfAbsent(player, k -> new Purse());
        long from = p.amount(fromId);
        long to = p.amount(toId);
        if (from < take || to > MoneyFormat.MAX - give) {
            return false;
        }
        if (from == take) {
            p.money.remove(fromId);
        } else {
            p.money.put(fromId, from - take);
        }
        if (to + give > 0) {
            p.money.put(toId, to + give);
        }
        setDirty();
        return true;
    }

    /** Empty every purse and forget every start money (history starts over: /ee reset confirm). Returns how many. */
    public synchronized int resetAll() {
        int n = players.size();
        if (n > 0) {
            players.clear();
            setDirty();
        }
        return n;
    }

    /** Empty this purse and forget the start money, so it comes again the next time the purse is used. */
    public synchronized void reset(UUID player) {
        if (players.remove(player) != null) {
            setDirty();
        }
    }

    public int playerCount() {
        return players.size();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Purse> e : players.entrySet()) {
            Purse p = e.getValue();
            CompoundTag pt = new CompoundTag();
            pt.putUUID("player", e.getKey());
            pt.putBoolean("started", p.started);
            pt.putInt("startDate", p.startDate);
            pt.putString("startMoney", p.startMoney == null ? "" : p.startMoney);
            pt.putLong("startAmount", p.startAmount);
            ListTag money = new ListTag();
            for (Map.Entry<String, Long> m : p.money.entrySet()) {
                if (m.getValue() == null || m.getValue() <= 0) {
                    continue;
                }
                CompoundTag mt = new CompoundTag();
                mt.putString("id", m.getKey());
                mt.putLong("amount", m.getValue());
                money.add(mt);
            }
            pt.put("money", money);
            list.add(pt);
        }
        tag.put("players", list);
        tag.putInt("format", 1);
        return tag;
    }

    public static PlayerPurses load(CompoundTag tag) {
        PlayerPurses purses = new PlayerPurses();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag pt = (CompoundTag) t;
            if (!pt.hasUUID("player")) {
                continue;
            }
            Purse p = new Purse();
            p.started = pt.getBoolean("started");
            p.startDate = pt.getInt("startDate");
            p.startMoney = pt.getString("startMoney");
            p.startAmount = pt.getLong("startAmount");
            for (Tag mtag : pt.getList("money", Tag.TAG_COMPOUND)) {
                CompoundTag mt = (CompoundTag) mtag;
                long amount = mt.getLong("amount");
                if (amount > 0) {
                    p.money.put(mt.getString("id"), Math.min(amount, MoneyFormat.MAX));
                }
            }
            purses.players.put(pt.getUUID("player"), p);
        }
        return purses;
    }
}
