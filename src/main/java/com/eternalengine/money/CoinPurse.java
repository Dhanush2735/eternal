package com.eternalengine.money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

/**
 * Step 23b: a player's money as pieces in the inventory: coins, notes, cowries, beans, cut silver
 * ({@link MoneyItem}). Paying hands pieces over and takes change back; being paid brings pieces (rounded down to the
 * smallest piece); the money-changer takes all pieces of one money. Barter and money counted in days of work have no
 * pieces: they stay a written tab in {@link PlayerPurses} ("goods worth about 2 days of work"). Written money of a
 * money with pieces (the start money, purses from before step 23b) becomes pieces when the purse is opened.
 */
public final class CoinPurse implements Wallet {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** At most this many pieces change hands at once (36 stacks of 64). */
    public static final long MOST_PIECES = 36L * 64L;

    private final ServerPlayer player;
    private final PlayerPurses purses;
    private final MoneyTable table;

    private CoinPurse(ServerPlayer player, PlayerPurses purses, MoneyTable table) {
        this.player = player;
        this.purses = purses;
        this.table = table;
    }

    /** A player's purse of pieces; written money of monies with pieces becomes pieces first. */
    public static CoinPurse of(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CoinPurse c = new CoinPurse(player, PlayerPurses.get(server), MoneyTable.get());
        c.strike();
        return c;
    }

    /**
     * Written amounts of monies with pieces become pieces in the inventory (each in the pieces of its year, or of
     * its nearest year of use for old money). Returns what was struck, in words, or null.
     */
    String strike() {
        PlayerPurses.Purse written = purses.view(player.getUUID());
        int now = PurseService.yearNow();
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : written.money().entrySet()) {
            MoneyTable.Money m = table.money(e.getKey());
            long amount = e.getValue() == null ? 0L : e.getValue();
            if (m == null || m.inDays() || amount <= 0) {
                continue;
            }
            int year = Denominations.yearFor(table, m, now);
            List<Denominations.Piece> pieces = Denominations.of(m, year);
            if (pieces.isEmpty()) {
                continue;
            }
            long biggest = pieces.get(pieces.size() - 1).value();
            long chunk = biggest > Long.MAX_VALUE / MOST_PIECES ? amount : Math.min(amount, biggest * MOST_PIECES);
            long got = give(m, pieces, chunk);
            purses.add(player.getUUID(), m.id(), -chunk);
            parts.add(PurseService.named(m, got, year) + (got < chunk ? " (" + (chunk - got) + " too small to strike)" : ""));
        }
        if (parts.isEmpty()) {
            return null;
        }
        String words = String.join("; ", parts);
        LOGGER.info("[EE money] {}'s written purse money became pieces: {}", player.getGameProfile().getName(), words);
        return words;
    }

    @Override
    public PlayerPurses.Purse view() {
        PlayerPurses.Purse w = purses.view(player.getUUID());
        PlayerPurses.Purse v = new PlayerPurses.Purse();
        v.started = w.started;
        v.startDate = w.startDate;
        v.startMoney = w.startMoney;
        v.startAmount = w.startAmount;
        for (Map.Entry<String, Long> e : totals().entrySet()) {
            v.money.merge(e.getKey(), e.getValue(), Long::sum);
        }
        for (Map.Entry<String, Long> e : w.money().entrySet()) {
            v.money.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return v;
    }

    @Override
    public boolean pay(String moneyId, long amount, int year) {
        if (amount <= 0) {
            return true;
        }
        MoneyTable.Money m = table.money(moneyId);
        if (m == null || m.inDays()) {
            return purses.add(player.getUUID(), moneyId, -amount) >= 0;
        }
        List<Denominations.Piece> pieces = Denominations.of(m, year);
        Denominations.Plan plan = Denominations.plan(held(moneyId), amount, pieces);
        if (plan == null) {
            return false;
        }
        for (Map.Entry<Long, Long> e : plan.taken().entrySet()) {
            remove(moneyId, e.getKey(), e.getValue());
        }
        if (plan.change() > 0) {
            give(m, pieces, plan.change());
        }
        player.containerMenu.broadcastChanges();
        return true;
    }

    @Override
    public long receive(String moneyId, long amount, int year) {
        if (amount <= 0) {
            return 0L;
        }
        MoneyTable.Money m = table.money(moneyId);
        if (m == null) {
            return -1L;
        }
        List<Denominations.Piece> pieces = Denominations.of(m, year);
        if (m.inDays() || pieces.isEmpty()) {
            return purses.add(player.getUUID(), moneyId, amount) < 0 ? -1L : amount;
        }
        if (Denominations.splitCount(pieces, amount) > MOST_PIECES) {
            return -1L;
        }
        long got = give(m, pieces, amount);
        player.containerMenu.broadcastChanges();
        return got;
    }

    @Override
    public long takeAll(String moneyId) {
        MoneyTable.Money m = table.money(moneyId);
        if (m == null || m.inDays()) {
            long have = purses.view(player.getUUID()).amount(moneyId);
            if (have > 0) {
                purses.add(player.getUUID(), moneyId, -have);
            }
            return Math.max(0L, have);
        }
        long sum = 0L;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(moneyId)) {
                sum += MoneyItem.value(s) * s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        long written = purses.view(player.getUUID()).amount(moneyId);
        if (written > 0) {
            purses.add(player.getUUID(), moneyId, -written);
            sum += written;
        }
        player.containerMenu.broadcastChanges();
        return sum;
    }

    // ------------------------------------------------------------------------------------------- the inventory

    /** Pieces held of one money: value to count. */
    public TreeMap<Long, Long> held(String moneyId) {
        TreeMap<Long, Long> out = new TreeMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(moneyId)) {
                out.merge(MoneyItem.value(s), (long) s.getCount(), Long::sum);
            }
        }
        return out;
    }

    /** What the pieces in the inventory are worth, by money. */
    public Map<String, Long> totals() {
        Map<String, Long> out = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (MoneyItem.isMoney(s)) {
                out.merge(MoneyItem.moneyId(s), MoneyItem.value(s) * s.getCount(), Long::sum);
            }
        }
        return out;
    }

    /** The pieces carried, by name: "1 x 1 rupee (Osmania sicca), 3 x 1 anna (Osmania sicca)"; "" when none. */
    public String piecesLine() {
        Map<String, Long> byName = new LinkedHashMap<>();
        Map<String, Long> valueOf = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (MoneyItem.isMoney(s)) {
                String name = s.getHoverName().getString();
                byName.merge(name, (long) s.getCount(), Long::sum);
                valueOf.putIfAbsent(name, MoneyItem.value(s));
            }
        }
        List<Map.Entry<String, Long>> list = new ArrayList<>(byName.entrySet());
        list.sort((a, b) -> Long.compare(valueOf.get(b.getKey()), valueOf.get(a.getKey())));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : list) {
            parts.add(e.getValue() + " x " + e.getKey());
        }
        return String.join(", ", parts);
    }

    /** Take every piece of money out of the inventory; returns how many pieces. */
    public int clear() {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MoneyItem) {
                n += s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        player.containerMenu.broadcastChanges();
        return n;
    }

    private void remove(String moneyId, long value, long count) {
        long left = count;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(moneyId) && MoneyItem.value(s) == value) {
                int take = (int) Math.min(left, s.getCount());
                s.shrink(take);
                if (s.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                left -= take;
            }
        }
        inv.setChanged();
    }

    /** Hand pieces making {@code amount} to the player (dropped at the feet when the inventory is full). */
    private long give(MoneyTable.Money m, List<Denominations.Piece> pieces, long amount) {
        Map<Long, Denominations.Piece> byValue = new LinkedHashMap<>();
        for (Denominations.Piece p : pieces) {
            byValue.put(p.value(), p);
        }
        long sum = 0L;
        for (long[] vn : Denominations.split(pieces, amount)) {
            Denominations.Piece p = byValue.get(vn[0]);
            long n = vn[1];
            while (n > 0 && p != null) {
                int c = (int) Math.min(64L, n);
                ItemStack s = MoneyItem.make(m, p, c);
                player.getInventory().add(s);
                if (!s.isEmpty()) {
                    player.drop(s, false);
                }
                n -= c;
                sum += c * vn[0];
            }
        }
        player.getInventory().setChanged();
        return sum;
    }
}
