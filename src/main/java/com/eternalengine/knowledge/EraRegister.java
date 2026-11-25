package com.eternalengine.knowledge;

import com.eternalengine.civilization.ReputationStanding;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The person register (the approved prototype's "Who is he?" screen), built line for line
 * from the prototype's five pages: who he is, his life, his day, what he knows, and the
 * events and town around him. Every line carries its mark — recorded, estimated, believed,
 * not recorded, not yet — and the page tallies them; the register is silent where it is
 * silent, and the tally says so.
 */
public final class EraRegister {

    private EraRegister() {
    }

    /** The register of a person at his place-year, or null when the pack is missing. */
    @Nullable
    public static RegisterView build(TalkService.Ctx c, @Nullable ReputationStanding.Standing.Person p,
                                     String tier, char hour) {
        if (!EraTalk.has()) {
            return null;
        }
        EraWorld.World w = EraTalk.at(c);
        if (w == null) {
            return null;
        }
        EraWorld.House h = EraTalk.ERA.house(c.lat, c.lon, KnowledgeDate.year(c.date));
        if (h == null) {
            return null;
        }
        EraTables.TradeDef t0 = EraTables.trade(c.role);
        EraWorld.Entry e = EraTalk.counter(c);
        char ec = EraTalk.eraClass(w.year());
        int age = w.age();
        String wk = EraTables.work(c.role);
        String born = EraTables.bornLbl(w.year(), age);
        String years = EraTables.yearLbl(w.year());
        int meetings = p == null ? 0 : p.meetings;

        RegisterView v = new RegisterView();
        v.entityId = c.entityId;
        v.name = c.npcName;
        v.date = w.date();
        v.strip = "Person register · " + c.npcName + " · your copy of " + w.date();
        String sealWord = w.place();
        if (sealWord.startsWith("the ")) {
            sealWord = sealWord.substring(4);
        }
        String sealFirst = sealWord.split(" ")[0];
        if (sealFirst.isEmpty()) {
            sealFirst = "TOWN";
        }
        v.sealTop = sealFirst.substring(0, Math.min(3, sealFirst.length())).toUpperCase();
        v.sealBottom = w.year() < 0 ? "-" : String.valueOf(Math.max(0, (w.year() / 100) * 100));
        v.dateRow = w.place() + " · " + w.ruler() + " · " + w.date();
        String town = switch (tier) {
            case "trusted" -> "easy — you are a good face";
            case "marked" -> "watching the road";
            case "wary" -> "on its watch";
            case "warm" -> "easy with you";
            default -> "quiet";
        };
        String mind = switch (hour) {
            case 'm' -> "The " + wk + " is waiting. The town is " + town + ".";
            case 'n' -> "The count is on the table. " + w.event() + " is what the town is saying.";
            case 'e' -> "The day is spent. " + capw(h.belief()) + " — that is what " + h.gather() + " is saying.";
            default -> "The lamp is low. The " + wk + " will be back in the morning. The town is " + town + ".";
        };
        v.mind = mind;
        v.mindSmall = "— what is in his mind " + EraTables.hourLbl(hour) + ", from his day and the town's news";

        List<List<RegisterView.Row>> tally = new ArrayList<>();

        // ------------------------------------------------------------ who he is
        List<RegisterView.Row> who = new ArrayList<>();
        who.add(r("Name", c.npcName + " — the town's own register for this era", "rec"));
        who.add(r("Sex", "male — body, face and name follow this one decision", "rec"));
        who.add(r("Age", age + " — the year stamp: born " + born + "; when the year turns, he turns with it", "rec"));
        who.add(r("Era · state", EraTables.regionName(w.region()) + ", " + years, "rec"));
        who.add(r("Trade", EraTables.roleWord(c.role)
                + (EraTalk.hasCounter(c.role) ? " — a common trade, era-coded to " + years : " — no trade but a life"), "rec"));
        who.add(r("Cloth today", EraTables.cloth(ec) + " — this place, this heat", "rec"));
        List<RegisterView.Row> lane = new ArrayList<>();
        for (EraWorld.Where we : w.where()) {
            lane.add(r(we.name() + " — " + EraTables.roleWord(we.role()), we.place(), "rec"));
        }
        List<RegisterView.Row> counter = new ArrayList<>();
        String sellNone = t0.sellNone().isEmpty() ? "no trade but a life" : t0.sellNone();
        sellNone = sellNone.replaceFirst("^I sell nothing — ", "")
                .replaceFirst("^Nothing on the street — ", "")
                .replaceFirst("^Nothing\\. I am not for sale, and I do not buy\\.", "not for sale");
        counter.add(r("Sells", t0.sell().isEmpty() ? "nothing — " + sellNone : String.join(", ", t0.sell()),
                t0.sell().isEmpty() ? "non" : "rec"));
        counter.add(r("Takes", t0.buy().isEmpty() ? "nothing" : String.join(", ", t0.buy()),
                t0.buy().isEmpty() ? "non" : "rec"));
        if (e != null) {
            if (!e.sell().isEmpty()) {
                List<String> pr = new ArrayList<>();
                for (EraWorld.Good g : e.sell()) {
                    pr.add(g.item() + " — " + g.price());
                }
                counter.add(r("Prices", String.join("; ", pr), "r".equals(e.tag()) ? "rec" : "est"));
            } else if (!e.note().isEmpty()) {
                counter.add(r("Prices", e.reg().isEmpty() ? "no trade at this counter — the lane keeps its own way" : e.reg(),
                        "h".equals(e.tag()) ? "rec" : (e.tag().isEmpty() ? "rec" : e.tag())));
            }
        }
        counter.add(r("The town's trade", t0.npc(), "rec"));
        String unlike = "soldier".equals(c.role) ? "merchant" : "guard".equals(c.role) ? "soldier" : "guard";
        counter.add(r("Temperament (drawn once, then fixed)",
                EraTables.temper(c.role) + " — a " + EraTables.roleWord(c.role) + " does not speak like a " + unlike, "est"));
        counter.add(r("Famous card", "none — an ordinary man, shown as a complete person; "
                + "a famous man would stand here with his card and his years", "rec"));
        v.tabs.add(new RegisterView.Tab("Who he is", List.of(
                new RegisterView.Section("", who, null),
                new RegisterView.Section("THE TRADES OF THE LANE", lane, null),
                new RegisterView.Section("HIS COUNTER", counter,
                        "The temperament line is what the repos proved: a fixed character the person speaks from, not a random mood each day."))));
        tally.add(who);
        tally.add(lane);
        tally.add(counter);

        // ------------------------------------------------------------ his life
        int hunger = hour == 'm' ? 2 : hour == 'n' ? 1 : 3;
        int strength = hour == 'm' ? 5 : hour == 'n' ? 4 : hour == 'e' ? 3 : 1;
        int content = (hour == 'e' ? 2 : 3) - ("marked".equals(tier) || "wary".equals(tier) ? 1 : 0);
        String ht = hunger == 1 ? "full — the morning meal was good"
                : hunger == 2 ? "a little hungry — the meal was at noon" : "hungry — the evening meal is near";
        String stt = strength >= 4 ? "the day's work is in the legs"
                : strength == 3 ? "steady, with the evening still ahead" : "the day is spent";
        String ct = content >= 3 ? "easy — the lane is calm"
                : content == 2 ? "low — the town is on its guard" : "guarded — the road is watched";
        String mt = switch (tier) {
            case "trusted" -> "a good face — the town says so with you";
            case "marked" -> "a watched face — the road says so";
            case "wary" -> "a watched face";
            case "warm" -> "a known face — the lane says so";
            default -> "a face the lane is learning";
        };
        List<RegisterView.Row> life = new ArrayList<>();
        life.add(r("Home", h.home(), "rec"));
        life.add(r("Wife", h.wife() + " — her name from the naming of this era", "rec"));
        life.add(r("Children", "none in the register — the household ledger is silent; no child without its own go", "non"));
        life.add(r("His years", "born " + born + " · at " + wk + " at 14 · this page, " + years, "est"));
        life.add(r("Hunger (needs)", needs(hunger) + " — " + ht, "rec"));
        life.add(r("Strength (needs)", needs(strength) + " — " + stt, "rec"));
        life.add(r("Contentment (needs)", needs(content) + " — " + ct, "rec"));
        life.add(r("Skill", (age - 14) + " years at " + wk + " — the trade counts him among the steady", "est"));
        life.add(r("His one hope", h.hope() + " — one goal, held quietly; it will be glad to be told once it comes true", "est"));
        life.add(r("In the town's eyes", "the people of " + h.gather() + " · the town's mark is " + w.landmark(), "rec"));
        life.add(r("You, in his eyes", meetings < 1 ? "not met yet — the lane is learning your face"
                : needs5(meetings) + " — " + mt, "rec"));
        v.tabs.add(new RegisterView.Tab("His life", List.of(
                new RegisterView.Section("", life,
                        "The life layer the repos proved — needs, skill, one hope, a few relationships, and the standing you hold with him."))));
        tally.add(life);

        // ------------------------------------------------------------ his day
        String items = EraTalk.itemsLine(c);
        List<RegisterView.Row> day = new ArrayList<>();
        day.add(r("Today's cloth", EraTables.cloth(ec) + " — this place, this heat", "rec"));
        day.add(r("06:00", "up; water at " + EraTables.water(ec) + ", a line of three ahead of him", "est"));
        day.add(r("06:30", "walks the lane to " + wk, "rec"));
        day.add(r("08:00", "work begins at " + wk, "rec"));
        day.add(r("12:00", "the work rests; a meal carried from home", "est"));
        day.add(r("13:00", "prayer, facing " + EraTables.prayer(w.region()), "rec"));
        day.add(r("15:00", "prayer again, then " + wk + " till the evening bell", "rec"));
        day.add(r("17:00", "the bell — the work rests, the town does not", "rec"));
        day.add(r("18:30", "meal at home; the day's count on the table", "est"));
        day.add(r("20:00", "the evening at " + h.gather(), "rec"));
        day.add(r("22:00", "home, the lamp low, sleep", "est"));
        day.add(r("Today's work", items.isEmpty() ? "nothing of his own — the day is the lane's" : items.replace("· ", " "), "est"));
        day.add(r("The town's hour", EraTables.mindHour(hour), "est"));
        day.add(r("The town's stage", h.stage() + " — the growth the town is living through", "est"));
        v.tabs.add(new RegisterView.Tab("His day", List.of(
                new RegisterView.Section("", day,
                        "The routine is his role-day. The town's hour and stage are the growing-town layer, read from the town's news."))));
        tally.add(day);

        // ------------------------------------------------------------ his memory
        String lastQ = p == null || p.lastQ == null || p.lastQ.isEmpty() ? "" : p.lastQ;
        List<RegisterView.Row> facts = new ArrayList<>();
        facts.add(r("The event of the day", w.event() + " — he saw it, or the town told the lane", "rec"));
        facts.add(r("The ruler", w.ruler() + " — " + w.court() + " — " + h.paper(), "rec"));
        facts.add(r("The mark of the town", w.landmark() + " — known to everyone in the lane", "rec"));
        facts.add(r("His trade", items.isEmpty() ? "nothing of his own — the lane is his trade — his own count"
                : items.replace("· ", " ") + " — his own count", "rec"));
        List<RegisterView.Row> beliefs = new ArrayList<>();
        beliefs.add(r("A talk from " + h.gather(), h.belief() + " — he holds it, but no record has it", "bel"));
        beliefs.add(r(ec == 'n' ? "The new order" : "The old ways",
                (ec == 'n' ? "the new order will outlast the old" : "the old ways will hold")
                        + " — he hopes; the ledger has not said so", "bel"));
        List<RegisterView.Row> ofYou = new ArrayList<>();
        ofYou.add(r("Your meetings", meetings < 1 ? "not met yet — the place is kept open"
                : needs5(meetings) + " — the warmth stays when you leave", "rec"));
        ofYou.add(r("Your last question", lastQ.isEmpty() ? "none yet — the place is kept open for it"
                : lastQ + " — it goes into his memory and comes out in his next talk", "rec"));
        ofYou.add(r("Changed history", "none — no fork has touched this world; when one lands, "
                + "this line is the first place you would look", "rec"));
        v.tabs.add(new RegisterView.Tab("His memory", List.of(
                new RegisterView.Section("WHAT HE KNOWS — FACTS", facts, null),
                new RegisterView.Section("WHAT HE BELIEVES — NOT YET FACTS", beliefs, null),
                new RegisterView.Section("WHAT HE REMEMBERS OF YOU", ofYou,
                        "Every remembered thing is a fact with its source, or a belief the page admits is only a belief — and what he remembers of you is his, to keep."))));
        tally.add(facts);
        tally.add(beliefs);
        tally.add(ofYou);

        // ------------------------------------------------------------ events and town
        List<RegisterView.Row> events = new ArrayList<>();
        events.add(r(years + " · the event", w.event() + " — his part: a man in the crowd, watching — "
                + "the event recorded; his slot estimated from the cast", "est"));
        events.add(r("The famous, at home in their years", w.famous() + " — of " + w.famousWho(), "rec"));
        events.add(r("The town's own mark", w.landmark() + " — the town's memory, kept apart from his", "rec"));
        events.add(r("Life events", "his wedding, and one day his funeral — still under \"and many more\", "
                + "awaiting its own go", "yet"));
        List<RegisterView.Row> around = new ArrayList<>();
        around.add(r("Under " + w.ruler(), h.stage(), "est"));
        around.add(r("What the town remembers", w.landmark() + " · the event of the day — "
                + "the town's own ledger, separate from his", "rec"));
        v.tabs.add(new RegisterView.Tab("Events & town", List.of(
                new RegisterView.Section("EVENTS", events, null),
                new RegisterView.Section("THE TOWN, AROUND HIM", around,
                        "A person acting in a real day of a real year, with the town's memory kept apart from his."))));
        tally.add(events);
        tally.add(around);

        // ------------------------------------------------------------ the tally
        for (List<RegisterView.Row> rows : tally) {
            for (RegisterView.Row row : rows) {
                v.lines++;
                switch (row.tag()) {
                    case "rec" -> v.rec++;
                    case "est" -> v.est++;
                    case "bel" -> v.bel++;
                    case "non" -> v.non++;
                    default -> v.yet++;
                }
            }
        }
        return v;
    }

    private static RegisterView.Row r(String label, String value, String tag) {
        return new RegisterView.Row(label, value, tag);
    }

    /** The five-mark of a need, in the words the page prints ("3 of 5"). */
    private static String needs(int n) {
        return Math.max(0, Math.min(5, n)) + " of 5";
    }

    /** The five-mark of the meetings ("2 of 5", the fifth kept open). */
    private static String needs5(int n) {
        return Math.max(0, Math.min(5, n)) + " of 5";
    }

    private static String capw(String s) {
        return s == null || s.isEmpty() ? s == null ? "" : s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
