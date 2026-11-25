package com.eternalengine.money;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 24: Minecraft's trade screen with a townsperson, as the server sends it: the trades on the left (what you give,
 * what you get), who trades, what he just said, what the two slots do right now and the money you carry.
 *
 * <p>{@link #offers} is the seller's list of wares (step 19c, kept for the shop's checks and commands); it stays on
 * the server. {@link #rows} are the trades the screen shows.
 */
public final class TradeView {

    /** The most wares one seller lists (step 19c). */
    public static final int MAX_OFFERS = 24;
    /** The most trades the screen shows (wares, goods you sell, money you change). */
    public static final int MAX_ROWS = 40;

    /**
     * One thing for sale. {@code item} is the item's registry name (for its picture), {@code price} the amount in the
     * smallest unit of {@code money} (step 24: "" is the money of the place; cheap things may be priced in the small
     * change taken beside it, cowries beside manillas); {@code left} how many more can be bought today (-1: no limit).
     */
    public record Offer(String id, String item, int count, String name, String shortName, String lore, String priceText,
                        long price, int left, boolean owned, String money) {
        public Offer(String id, String item, int count, String name, String shortName, String lore, String priceText,
                     long price, int left, boolean owned) {
            this(id, item, count, name, shortName, lore, priceText, price, left, owned, "");
        }
    }

    /** What a trade does: buy his goods, sell yours, change money, make pieces bigger or smaller, swap goods. */
    public enum Kind {
        BUY, SELL, CHANGE, MAKE, BREAK, SWAP
    }

    /** An item's picture: its registry name, how many, and its data (a coin's name); empty when {@code item} is "". */
    public record Stack(String item, int count, @Nullable CompoundTag tag) {
        public static final Stack NONE = new Stack("", 0, null);

        public boolean isEmpty() {
            return item == null || item.isEmpty() || count <= 0;
        }
    }

    /**
     * One trade on the left: what you put in (one or two pictures; {@code moreCost} when it takes more kinds of pieces
     * than two), what you get ({@code morePay}: more pieces come with it), its name, its price in words, a few words
     * about it, how many are left today (-1: no limit) and whether you have it already.
     */
    public record Row(String id, Kind kind, Stack cost1, Stack cost2, boolean moreCost, Stack result, boolean morePay,
                      String name, String price, String lore, int left, boolean owned) {
        /** False when it cannot be had now (sold out today, or you have it already). */
        public boolean open() {
            return !owned && left != 0;
        }
    }

    public int entityId;
    /** The trade menu this view belongs to. */
    public int containerId = -1;
    public String npcName = "";
    public String title = "";
    public String job = "";
    public String dateLabel = "";
    /** The trader's words: a greeting, a receipt or a refusal. */
    public String says = "";
    /** What the two slots do for the picked trade right now ("You pay 1 rupee. Your change: 14 annas."). */
    public String status = "";
    /** The money the player carries, in words. */
    public String purse = "";
    /** The picked trade (index into {@link #rows}), -1 for none. */
    public int selected = -1;
    /** 1 after a trade (a happy sound), 2 after a refusal, 0 otherwise. */
    public int sound;
    /** The seller's wares (server only, never sent). */
    public final List<Offer> offers = new ArrayList<>();
    public final List<Row> rows = new ArrayList<>();

    public Offer offer(String id) {
        for (Offer o : offers) {
            if (o.id().equals(id)) {
                return o;
            }
        }
        return null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(containerId);
        buf.writeUtf(cut(npcName, 128), 128);
        buf.writeUtf(cut(title, 128), 128);
        buf.writeUtf(cut(job, 64), 64);
        buf.writeUtf(cut(dateLabel, 64), 64);
        buf.writeUtf(cut(says, 600), 600);
        buf.writeUtf(cut(status, 400), 400);
        buf.writeUtf(cut(purse, 300), 300);
        buf.writeVarInt(selected + 1);
        buf.writeVarInt(sound);
        int n = Math.min(MAX_ROWS, rows.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            buf.writeUtf(cut(r.id(), 64), 64);
            buf.writeVarInt(r.kind().ordinal());
            writeStack(buf, r.cost1());
            writeStack(buf, r.cost2());
            buf.writeBoolean(r.moreCost());
            writeStack(buf, r.result());
            buf.writeBoolean(r.morePay());
            buf.writeUtf(cut(r.name(), 96), 96);
            buf.writeUtf(cut(r.price(), 160), 160);
            buf.writeUtf(cut(r.lore(), 400), 400);
            buf.writeVarInt(r.left() + 1);
            buf.writeBoolean(r.owned());
        }
    }

    public static TradeView decode(FriendlyByteBuf buf) {
        TradeView v = new TradeView();
        v.entityId = buf.readVarInt();
        v.containerId = buf.readVarInt();
        v.npcName = buf.readUtf(128);
        v.title = buf.readUtf(128);
        v.job = buf.readUtf(64);
        v.dateLabel = buf.readUtf(64);
        v.says = buf.readUtf(600);
        v.status = buf.readUtf(400);
        v.purse = buf.readUtf(300);
        v.selected = buf.readVarInt() - 1;
        v.sound = buf.readVarInt();
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        Kind[] kinds = Kind.values();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(64);
            int k = buf.readVarInt();
            Stack c1 = readStack(buf);
            Stack c2 = readStack(buf);
            boolean moreCost = buf.readBoolean();
            Stack res = readStack(buf);
            boolean morePay = buf.readBoolean();
            String name = buf.readUtf(96);
            String price = buf.readUtf(160);
            String lore = buf.readUtf(400);
            int left = buf.readVarInt() - 1;
            boolean owned = buf.readBoolean();
            v.rows.add(new Row(id, kinds[Math.max(0, Math.min(kinds.length - 1, k))], c1, c2, moreCost, res, morePay,
                    name, price, lore, left, owned));
        }
        return v;
    }

    static void writeStack(FriendlyByteBuf buf, Stack s) {
        boolean has = s != null && !s.isEmpty();
        buf.writeBoolean(has);
        if (has) {
            buf.writeUtf(cut(s.item(), 96), 96);
            buf.writeVarInt(Math.max(1, Math.min(9999, s.count())));
            buf.writeNbt(s.tag());
        }
    }

    static Stack readStack(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return Stack.NONE;
        }
        return new Stack(buf.readUtf(96), buf.readVarInt(), buf.readNbt());
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
