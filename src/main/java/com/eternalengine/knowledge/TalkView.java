package com.eternalengine.knowledge;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One state of the talk screen, made by the server: what the villager says, the numbered options and
 * the "Ask about" names. Text may hold links {@code [[cardId|shown words]]}; the screen draws them as
 * gold, clickable names.
 */
public final class TalkView {

    public static final Pattern LINK = Pattern.compile("\\[\\[(\\w+)\\|([^\\]]+)\\]\\]");
    private static final int MAX_TEXT = 4000;
    private static final int MAX_ITEMS = 16;

    /** A numbered choice. {@code action}: ask, card, topic, topics, back, free. */
    public record Option(String action, String a, String b, String label) {
    }

    /** One of the sheet's chips (the approved prototype's chip row): its action and its words. */
    public record Chip(String action, String label) {
    }

    public record Name(String cardId, String label) {
    }

    public int entityId;
    public String npcName = "";
    public String job = "";
    public int level = 1;
    public String dateLabel = "";
    public String cardTitle = "";
    public String cardKind = "";
    public String text = "";
    /** A gray line under the text, e.g. "King Kothi Palace: about 3.5 km north of here". */
    public String hint = "";
    public boolean canBack;
    /**
     * Step 21: the talk is over because the person has gone (or the player walked away): the box only says so and
     * offers Goodbye. Set by the server when an action arrives for somebody who is no longer there, and by the screens
     * when they see the person go.
     */
    public boolean ended;
    public final List<Option> options = new ArrayList<>();
    public final List<Name> names = new ArrayList<>();
    /** The sheet's chips (the approved prototype's chip row); when they are here, the numbered
     * questions are not — the chips are the sheet's way to ask. */
    public final List<Chip> chips = new ArrayList<>();
    /** The "Ask where" pick: the role the player has chosen (empty until he picks one). */
    public String findRole = "";
    // The interaction sheet: the mood line, and the player's standing for the debug screen (the values are
    // shown only there, for now).
    public String mood = "";
    public int moodDot = 0xFF5D7A8A;
    public int posRep;
    public int negRep;
    public String tier = "neutral";
    public int meetings;
    public String debugYou = "";
    public String debugLastQ = "";
    public int debugNews;
    public int debugAskRun;
    public int debugBond;
    /** The big action button's word (the sheet's red box): the trade's verb — "Trade", "Weapons", …;
     * empty when the trade has none and the button carries the job name. */
    public String actionLabel = "";
    /** What the trade carries (the sheet's roles band items), dim under the band; empty when none. */
    public String items = "";
    /**
     * The sheet's red box, and what it does: "" the box is absent (the role holds no counter);
     * "container" it opens Minecraft's trade screen (a real container, real coin); "counter" it opens
     * the person's counter as the lane describes it (the era's goods and prices, or the honest line).
     */
    public String actionKind = "";
    /**
     * Server only (never encoded): the trade screen the talk just opened. The packet layer sends
     * it its own packet (the sheet's trade screen, after the talk state), and the client shows it
     * over the talk box.
     */
    public TradePanelView tradePanel;

    /**
     * Step 21: the closing view of a talk: the same name, job and date, one line ("Lakshmi has left."), no
     * questions, no names, only Goodbye.
     */
    public static TalkView ended(int entityId, String npcName, String job, int level, String dateLabel, String line) {
        TalkView v = new TalkView();
        v.entityId = entityId;
        v.npcName = npcName == null ? "" : npcName;
        v.job = job == null ? "" : job;
        v.level = level;
        v.dateLabel = dateLabel == null ? "" : dateLabel;
        v.text = line == null ? "" : line;
        v.canBack = false;
        v.ended = true;
        return v;
    }

    /** The text without link marks, for the speech bubble and the chat. */
    public String plainText() {
        return plain(text);
    }

    public static String plain(String s) {
        return s == null ? "" : LINK.matcher(s).replaceAll("$2");
    }

    /** Card ids linked in a text, in order, without repeats. */
    public static List<String[]> links(String s) {
        List<String[]> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        Matcher m = LINK.matcher(s);
        while (m.find()) {
            boolean seen = false;
            for (String[] x : out) {
                seen |= x[0].equals(m.group(1));
            }
            if (!seen) {
                out.add(new String[]{m.group(1), m.group(2)});
            }
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(npcName, 128);
        buf.writeUtf(job, 64);
        buf.writeVarInt(level);
        buf.writeUtf(dateLabel, 64);
        buf.writeUtf(cardTitle, 160);
        buf.writeUtf(cardKind, 32);
        buf.writeUtf(cut(text, MAX_TEXT), MAX_TEXT);
        buf.writeUtf(cut(hint, 300), 300);
        buf.writeBoolean(canBack);
        buf.writeBoolean(ended);
        int n = Math.min(MAX_ITEMS, options.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Option o = options.get(i);
            buf.writeUtf(o.action(), 16);
            buf.writeUtf(o.a() == null ? "" : o.a(), 96);
            buf.writeUtf(o.b() == null ? "" : o.b(), 96);
            buf.writeUtf(cut(o.label(), 120), 120);
        }
        int m = Math.min(MAX_ITEMS, names.size());
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) {
            buf.writeUtf(names.get(i).cardId(), 96);
            buf.writeUtf(cut(names.get(i).label(), 80), 80);
        }
        int ch = Math.min(12, chips.size());
        buf.writeVarInt(ch);
        for (int i = 0; i < ch; i++) {
            buf.writeUtf(cut(chips.get(i).action(), 16), 16);
            buf.writeUtf(cut(chips.get(i).label(), 80), 80);
        }
        buf.writeUtf(cut(findRole, 32), 32);
        buf.writeUtf(cut(mood, 80), 80);
        buf.writeVarInt(moodDot);
        buf.writeVarInt(posRep);
        buf.writeVarInt(negRep);
        buf.writeUtf(cut(tier, 16), 16);
        buf.writeVarInt(meetings);
        buf.writeUtf(cut(debugYou, 48), 48);
        buf.writeUtf(cut(debugLastQ, 48), 48);
        buf.writeVarInt(debugNews);
        buf.writeVarInt(debugAskRun);
        buf.writeVarInt(debugBond);
        buf.writeUtf(cut(actionLabel, 24), 24);
        buf.writeUtf(cut(items, 96), 96);
        buf.writeUtf(cut(actionKind, 16), 16);
    }

    public static TalkView decode(FriendlyByteBuf buf) {
        TalkView v = new TalkView();
        v.entityId = buf.readVarInt();
        v.npcName = buf.readUtf(128);
        v.job = buf.readUtf(64);
        v.level = buf.readVarInt();
        v.dateLabel = buf.readUtf(64);
        v.cardTitle = buf.readUtf(160);
        v.cardKind = buf.readUtf(32);
        v.text = buf.readUtf(MAX_TEXT);
        v.hint = buf.readUtf(300);
        v.canBack = buf.readBoolean();
        v.ended = buf.readBoolean();
        int n = Math.min(MAX_ITEMS, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.options.add(new Option(buf.readUtf(16), buf.readUtf(96), buf.readUtf(96), buf.readUtf(120)));
        }
        int m = Math.min(MAX_ITEMS, buf.readVarInt());
        for (int i = 0; i < m; i++) {
            v.names.add(new Name(buf.readUtf(96), buf.readUtf(80)));
        }
        int ch = Math.min(12, buf.readVarInt());
        for (int i = 0; i < ch; i++) {
            v.chips.add(new Chip(buf.readUtf(16), buf.readUtf(80)));
        }
        v.findRole = buf.readUtf(32);
        v.mood = buf.readUtf(80);
        v.moodDot = buf.readVarInt();
        v.posRep = buf.readVarInt();
        v.negRep = buf.readVarInt();
        v.tier = buf.readUtf(16);
        v.meetings = buf.readVarInt();
        v.debugYou = buf.readUtf(48);
        v.debugLastQ = buf.readUtf(48);
        v.debugNews = buf.readVarInt();
        v.debugAskRun = buf.readVarInt();
        v.debugBond = buf.readVarInt();
        v.actionLabel = buf.readUtf(24);
        v.items = buf.readUtf(96);
        v.actionKind = buf.readUtf(16);
        return v;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
