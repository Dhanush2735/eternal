package com.eternalengine.knowledge;

import com.eternalengine.civilization.ReputationStanding;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The sheet's chips and their lines, the approved prototype's own words: the chip row of the
 * talk box (what to ask next, the trade, the parting), the trade line of a role that has no
 * priced counter, the lane's trades, the family line, the id card, and the parting.
 *
 * <p>Every word is the prototype's; nothing here is a rewording.
 */
public final class EraChips {

    /** The parting, the prototype's doBye line. */
    public static final String BYE = "Go well. The lane will keep your name.";

    /** The no-trade trade line (the prototype's tradeLine, when the role carries nothing). */
    public static final String NO_TRADE_LINE = "I have no trade of my own. The merchant down the lane "
            + "takes goods for a cut — I will point you to him, that is a kindness I will give.";

    private EraChips() {
    }

    /**
     * The sheet's chip row for this person, in the prototype's order and words: "Go on." when a
     * news has gone, the event by its first word, the ruler, the day's sight, how the trade is,
     * "Let's swap goods." (the counter, or the trade line), the lane's trades, the family when
     * the bond has it, and "Say no more." Null when the pack is absent — then the numbered
     * questions stand as before.
     */
    @Nullable
    public static List<TalkView.Chip> chips(TalkService.Ctx c, @Nullable ReputationStanding.Standing.Person p) {
        if (!EraTalk.has()) {
            return null;
        }
        EraWorld.World w = EraTalk.at(c);
        if (w == null) {
            return null;
        }
        List<TalkView.Chip> out = new ArrayList<>();
        if (p != null && p.newsCount >= 1) {
            out.add(new TalkView.Chip("more", "Go on."));
        }
        out.add(new TalkView.Chip("event", eventChip(w)));
        out.add(new TalkView.Chip("ruler", "Who rules here?"));
        out.add(new TalkView.Chip("landmark", "What did you see today?"));
        String how = "farmer".equals(c.role) ? "grain"
                : "weaver".equals(c.role) ? "cotton"
                : "citizen".equals(c.role) ? "lane" : "trade";
        out.add(new TalkView.Chip("howtrade", "How's the " + how + "?"));
        if (EraTalk.hasCounter(c.role)) {
            out.add(new TalkView.Chip("swap", "Let's swap goods."));
        } else if (!"citizen".equals(c.role)) {
            out.add(new TalkView.Chip("howtrade", "Let's swap goods."));
        }
        if (!w.where().isEmpty()) {
            out.add(new TalkView.Chip("lane", "The trades of the lane?"));
        }
        if (p != null && p.bond >= 3) {
            out.add(new TalkView.Chip("family", "You — and your family?"));
        }
        out.add(new TalkView.Chip("bye", "Say no more."));
        return out;
    }

    /** The event chip's word: the first comma of the event, without its "the". */
    public static String eventChip(EraWorld.World w) {
        String e = w.event().split(",")[0];
        if (e.startsWith("the ")) {
            e = e.substring(4);
        }
        return e + "?";
    }

    /**
     * The sheet's trade line (the prototype's tradeLine): what the trade carries, dressed for the
     * era; the no-trade line where the role carries nothing.
     */
    public static String tradeLine(TalkService.Ctx c) {
        String it = EraTalk.itemsLine(c);
        EraWorld.World w = EraTalk.at(c);
        if (it == null || it.isEmpty() || w == null) {
            return NO_TRADE_LINE;
        }
        char ec = EraTalk.eraClass(w.year());
        return "The price is the price — " + (ec == 'n' ? "the market makes it, not I" : "the season makes it, not I")
                + ". Have the coin ready. My trade carries: " + it + ".";
    }

    /** The family line (the prototype's familyLine): the house, the one who keeps it, the temper. */
    public static String familyLine(TalkService.Ctx c) {
        EraWorld.House h = house(c);
        if (h == null) {
            return "The house is small, and the temper of it is steady. That is all I ask of it.";
        }
        return "The house is small — " + h.home() + ". " + h.wife() + " keeps it. The temper of it is steady, "
                + "and that is all a " + EraTables.roleWord(c.role) + " asks of it.";
    }

    /** The lane's trades (the prototype's tradesAll): the where-register, and the walk. */
    public static String laneLine(TalkService.Ctx c) {
        EraWorld.World w = EraTalk.at(c);
        if (w == null || w.where().isEmpty()) {
            return "The lane keeps its trades — ask them in person.";
        }
        List<String> parts = new ArrayList<>();
        for (EraWorld.Where e : w.where()) {
            parts.add(e.name() + ", " + EraTables.work(e.role()) + ", " + e.place());
        }
        return "The lane keeps its trades — " + String.join("; ", parts)
                + ". Go in person. A pointed road is still one you walk.";
    }

    /** The id card (the sheet's SELF, the prototype's "who are you"): who he is, and the place. */
    @Nullable
    public static String idCard(TalkService.Ctx c) {
        EraWorld.World w = EraTalk.at(c);
        if (w == null) {
            return null;
        }
        if (EraTalk.hasCounter(c.role)) {
            return "I am " + c.npcName + ", " + EraTables.roleWord(c.role) + ". I have kept this trade in "
                    + w.place() + ", and the place knows me by it.";
        }
        return "I am " + c.npcName + ". No trade of my own — " + w.place() + " is where I live, "
                + "and I know it by the faces in the lane, not the counters.";
    }

    private static EraWorld.House house(TalkService.Ctx c) {
        if (!EraTalk.has()) {
            return null;
        }
        return EraTalk.ERA.house(c.lat, c.lon, KnowledgeDate.year(c.date));
    }
}
