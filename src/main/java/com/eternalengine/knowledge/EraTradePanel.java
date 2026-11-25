package com.eternalengine.knowledge;

import com.eternalengine.civilization.ReputationStanding;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The sheet's trade screen (the approved prototype's openTrade): the person's line (the
 * standing decides it, or the honest line), his three sells with the prices of the
 * place-year, his three takes, the "gives · takes" line, and the pocket.
 *
 * <p>Opening it is the trade: the record, the bond, and the meter move on the open, the way
 * the prototype does it.
 */
public final class EraTradePanel {

    private EraTradePanel() {
    }

    /**
     * The trade screen of a person at his place-year. Null when the role holds no counter
     * (the red box is absent for him) or the pack is missing.
     */
    @Nullable
    public static TradePanelView panel(TalkService.Ctx c, String tier) {
        if (!EraTalk.has()) {
            return null;
        }
        EraWorld.Entry e = EraTalk.counter(c);
        EraWorld.World w = EraTalk.at(c);
        if (e == null || w == null || !EraTalk.hasCounter(c.role)) {
            return null;
        }
        TradePanelView v = new TradePanelView();
        v.entityId = c.entityId;
        v.who = c.npcName + ": ";
        v.date = w.date();
        v.title = EraTables.roleName(c.role);
        v.back = "‹ Back to " + c.npcName;

        List<String> sellIt = new ArrayList<>();
        List<String> buyIt = new ArrayList<>();
        List<String> sellPr = new ArrayList<>();
        String eraCarry = null;
        String eraNote = null;
        EraTables.TradeDef t0 = EraTables.trade(c.role);
        char ec = EraTalk.eraClass(w.year());

        if (!e.sell().isEmpty()) {
            for (EraWorld.Good g : e.sell()) {
                sellIt.add(g.item());
                sellPr.add(g.price());
            }
            for (EraWorld.Good g : e.buy()) {
                buyIt.add(g.item());
            }
            List<String> carry = new ArrayList<>();
            for (EraWorld.Good g : e.sell()) {
                carry.add(g.price().isEmpty() ? g.item() : g.item() + ", at " + g.price());
            }
            eraCarry = String.join("; ", carry);
        } else if (!e.note().isEmpty()) {
            eraNote = e.note();
        } else {
            sellIt.addAll(t0.sell());
            buyIt.addAll(t0.buy());
        }
        if (sellIt.size() > 3) {
            sellIt = new ArrayList<>(sellIt.subList(0, 3));
            sellPr = new ArrayList<>(sellPr.subList(0, 3));
        }
        if (buyIt.size() > 3) {
            buyIt = new ArrayList<>(buyIt.subList(0, 3));
        }
        v.sell.addAll(sellIt);
        v.price.addAll(sellPr);
        v.buy.addAll(buyIt);

        String it = EraTalk.itemsLine(c);
        String said;
        if (eraNote != null) {
            said = eraNote;
        } else if ("trusted".equals(tier)) {
            said = "The goods are yours, and your face is a good one to sell to. Have them ready.";
        } else if ("marked".equals(tier)) {
            said = "I am dealing with you for the goods, not for your face. Have the coin ready.";
        } else {
            said = "The price is the price — " + (ec == 'n' ? "the market makes it, not I" : "the season makes it, not I")
                    + ". My trade carries: " + (eraCarry != null ? eraCarry : it.replace("· ", ", ")) + ".";
        }
        v.said = said;

        if (!sellIt.isEmpty() && !buyIt.isEmpty()) {
            v.status = "Gives " + first2(sellIt) + " · takes " + first2(buyIt);
        } else if (eraNote != null) {
            v.status = "No trade at this counter — " + (e.reg().isEmpty() ? "the lane keeps its own way" : e.reg());
        } else {
            v.status = "No trade at this counter — a life, not a shop.";
        }
        v.pocket = "You carry: " + EraTalk.pocket(w.year()) + ".";
        return v;
    }

    /** The trade, made: the record and the bond on this person, the meter for the city. */
    public static void exec(TalkService.Ctx c, @Nullable ReputationStanding.Standing.Person p) {
        if (p != null) {
            p.tradeUsed = true;
            p.bond++;
        }
        if (c.player != null && c.server != null && c.npc != null) {
            ReputationStanding.get(c.server.overworld()).tradeDone(c.player, c.npc.getUUID(), c.date, 10);
        }
    }

    private static String first2(List<String> l) {
        return String.join(", ", l.subList(0, Math.min(2, l.size())));
    }
}
