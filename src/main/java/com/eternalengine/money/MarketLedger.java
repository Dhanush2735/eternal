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
 * Step 19b: what each merchant has bought today, saved with the world, so leaving and coming back does not refill
 * his money. A merchant's day is a Minecraft day (the overworld's day count), whatever the speed of history; it is
 * also a new day when the money of the place changes (a new year, a new ruler). Days older than yesterday are
 * dropped.
 */
public final class MarketLedger extends SavedData {

    public static final String DATA_NAME = "eternalengine_market";

    /** One merchant's day (a copy when handed out). */
    public static final class Day {
        long day = Long.MIN_VALUE;
        String money = "";
        long spent;
        final Map<String, Integer> sold = new LinkedHashMap<>();

        /** The Minecraft day this is. */
        public long day() { return day; }
        /** The money he paid in. */
        public String money() { return money; }
        /** What he has paid out today, in that money's smallest unit. */
        public long spent() { return spent; }

        /** How many of a good he has bought today. */
        public int sold(String good) {
            Integer n = sold.get(good);
            return n == null ? 0 : n;
        }

        Day copy() {
            Day d = new Day();
            d.day = day;
            d.money = money;
            d.spent = spent;
            d.sold.putAll(sold);
            return d;
        }
    }

    private final Map<UUID, Day> merchants = new ConcurrentHashMap<>();

    public static MarketLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketLedger::load, MarketLedger::new, DATA_NAME);
    }

    /** This merchant's day so far (a copy); a fresh day when it is another day or another money. */
    public synchronized Day today(UUID merchant, long day, String money) {
        Day d = merchants.get(merchant);
        if (d == null || d.day != day || !d.money.equals(money)) {
            Day fresh = new Day();
            fresh.day = day;
            fresh.money = money;
            return fresh;
        }
        return d.copy();
    }

    /** He bought {@code count} of a good for {@code paid} today. */
    public synchronized void record(UUID merchant, long day, String money, String good, int count, long paid) {
        Day d = merchants.get(merchant);
        if (d == null || d.day != day || !d.money.equals(money)) {
            d = new Day();
            d.day = day;
            d.money = money;
            merchants.put(merchant, d);
        }
        d.spent += Math.max(0L, paid);
        d.sold.merge(good, Math.max(0, count), Integer::sum);
        merchants.values().removeIf(x -> x.day < day - 1);
        setDirty();
    }

    /** Forget every merchant's day (a reset of history); returns how many there were. */
    public synchronized int clear() {
        int n = merchants.size();
        if (n > 0) {
            merchants.clear();
            setDirty();
        }
        return n;
    }

    public int size() {
        return merchants.size();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Day> e : merchants.entrySet()) {
            Day d = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("merchant", e.getKey());
            t.putLong("day", d.day);
            t.putString("money", d.money);
            t.putLong("spent", d.spent);
            CompoundTag sold = new CompoundTag();
            d.sold.forEach(sold::putInt);
            t.put("sold", sold);
            list.add(t);
        }
        tag.put("merchants", list);
        tag.putInt("format", 1);
        return tag;
    }

    public static MarketLedger load(CompoundTag tag) {
        MarketLedger ledger = new MarketLedger();
        for (Tag x : tag.getList("merchants", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            if (!t.hasUUID("merchant")) {
                continue;
            }
            Day d = new Day();
            d.day = t.getLong("day");
            d.money = t.getString("money");
            d.spent = Math.max(0L, t.getLong("spent"));
            CompoundTag sold = t.getCompound("sold");
            for (String k : sold.getAllKeys()) {
                d.sold.put(k, Math.max(0, sold.getInt(k)));
            }
            ledger.merchants.put(t.getUUID("merchant"), d);
        }
        return ledger;
    }
}
