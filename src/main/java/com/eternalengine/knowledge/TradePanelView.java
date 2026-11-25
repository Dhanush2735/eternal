package com.eternalengine.knowledge;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The sheet's trade screen (the approved prototype's openTrade, the exact look of the trade
 * menu): what the person says (the standing decides it, or the honest line), his sells and
 * takes with the prices of the place-year, the "gives · takes" line, and the pocket.
 *
 * <p>Opening it is the trade: the record, the bond, and the meter move when it opens (the
 * prototype does it on the open).
 */
public final class TradePanelView {

    public int entityId;
    /** "Satya Naidu: " — who speaks. */
    public String who = "";
    /** What the person says at the counter; a row click replaces it with its own line. */
    public String said = "";
    /** The band's date line. */
    public String date = "";
    /** The title of the trades (the role's name). */
    public String title = "";
    /** "‹ Back to …" */
    public String back = "";
    /** "Gives … · takes …" or the honest line. */
    public String status = "";
    /** "You carry: …" */
    public String pocket = "";
    /** His sells, at most three (the prototype's three trade rows). */
    public final List<String> sell = new ArrayList<>();
    /** The prices of the sells, word for word (empty where a row has none). */
    public final List<String> price = new ArrayList<>();
    /** His takes, at most three. */
    public final List<String> buy = new ArrayList<>();

    /**
     * What a row says when it is picked (the prototype's row line): the good is yours, the
     * price and the coin; or the honest line where the row is empty.
     */
    public String rowLine(int i) {
        if (i < 0 || i >= sell.size() || sell.get(i) == null || sell.get(i).isEmpty()) {
            return "There is nothing at this counter for the coin.";
        }
        String item = sell.get(i);
        String price = i < this.price.size() ? this.price.get(i) : "";
        return "It is done. " + capw(item) + " is yours — "
                + (price.isEmpty() ? "" : price + ", ")
                + "the coin is mine, and the lane knows it.";
    }

    private static String capw(String s) {
        return s == null || s.isEmpty() ? s == null ? "" : s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(cut(who, 96), 96);
        buf.writeUtf(cut(said, 400), 400);
        buf.writeUtf(cut(date, 96), 96);
        buf.writeUtf(cut(title, 48), 48);
        buf.writeUtf(cut(back, 96), 96);
        buf.writeUtf(cut(status, 300), 300);
        buf.writeUtf(cut(pocket, 120), 120);
        buf.writeVarInt(Math.min(3, sell.size()));
        for (int i = 0; i < Math.min(3, sell.size()); i++) {
            buf.writeUtf(cut(sell.get(i), 64), 64);
        }
        buf.writeVarInt(Math.min(3, price.size()));
        for (int i = 0; i < Math.min(3, price.size()); i++) {
            buf.writeUtf(cut(price.get(i), 96), 96);
        }
        buf.writeVarInt(Math.min(3, buy.size()));
        for (int i = 0; i < Math.min(3, buy.size()); i++) {
            buf.writeUtf(cut(buy.get(i), 64), 64);
        }
    }

    public static TradePanelView decode(FriendlyByteBuf buf) {
        TradePanelView v = new TradePanelView();
        v.entityId = buf.readVarInt();
        v.who = buf.readUtf(96);
        v.said = buf.readUtf(400);
        v.date = buf.readUtf(96);
        v.title = buf.readUtf(48);
        v.back = buf.readUtf(96);
        v.status = buf.readUtf(300);
        v.pocket = buf.readUtf(120);
        int sellCount = readCount(buf, "sell");
        for (int i = 0; i < sellCount; i++) {
            v.sell.add(buf.readUtf(64));
        }
        int priceCount = readCount(buf, "price");
        for (int i = 0; i < priceCount; i++) {
            v.price.add(buf.readUtf(96));
        }
        int buyCount = readCount(buf, "buy");
        for (int i = 0; i < buyCount; i++) {
            v.buy.add(buf.readUtf(64));
        }
        return v;
    }
    private static int readCount(FriendlyByteBuf buf, String field) {
        int count = buf.readVarInt();
        if (count < 0 || count > 3) throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        return count;
    }

}
