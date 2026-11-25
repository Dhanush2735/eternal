package com.eternalengine.money;

import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step 19a: the purse as the player meets it. Where they stand and the game's date give the money of the place
 * ({@link MoneyTable}); the first time the purse is used it gets a day's pay in that money; and the purse and the
 * money of the place are put in plain words. No line says where a number came from.
 */
public final class PurseService {

    private PurseService() {
    }

    /** Where a player stands on Earth as {latitude, longitude}, or null when Terra++ cannot place them. */
    public static double[] placeOf(ServerPlayer player) {
        try {
            if (player == null || !TerraBridge.isAvailable()) {
                return null;
            }
            TerraBridge.GeoPoint geo = TerraCoordinateHelper.toGeoPoint(player.blockPosition());
            if (geo == null || !geo.valid()) {
                return null;
            }
            return new double[] {geo.latitude(), geo.longitude()};
        } catch (Throwable t) {
            return null;
        }
    }

    /** The money at a place (null: unknown place) in a year. */
    public static MoneyTable.Here here(double[] place, int year) {
        MoneyTable table = MoneyTable.get();
        return place == null ? table.at(Double.NaN, Double.NaN, year) : table.at(place[0], place[1], year);
    }

    /** The game's year today (the world's calendar). */
    public static int yearNow() {
        return KnowledgeDate.year(KnowledgeDate.today());
    }

    /**
     * Give the start money (a day's pay in the money of the place) if this purse never had it.
     *
     * @return the line telling the player, or null when the purse had it already
     */
    public static String startIfNew(PlayerPurses purses, UUID player, MoneyTable.Here here, int date) {
        long amount = startAmount(here);
        if (!purses.startIfNew(player, here.money().id(), amount, date)) {
            return null;
        }
        String what = MoneyFormat.amount(here.money(), amount, here.year());
        if (here.money().barter()) {
            return "You start with " + what + " to swap.";
        }
        return here.money().inDays() ? "You start with " + what + "."
                : "You start with " + named(here.money(), amount, here.year()) + ", about a day's pay here.";
    }

    /**
     * Step 23b: the start money: a day's pay here, rounded to the smallest piece of the money (so it can be held in
     * pieces: $9.38 in Quito in 2010 is $9.40 when the smallest piece is 5 cents).
     */
    public static long startAmount(MoneyTable.Here here) {
        long amount = here.dayPay();
        long piece = Denominations.smallest(here.money(), here.year());
        return piece > 1 ? Math.max(piece, Math.round(amount / (double) piece) * piece) : amount;
    }

    /** The purse and the money of the place, in plain words, for /ee purse. */
    public static List<String> describe(MoneyTable table, PlayerPurses.Purse purse, MoneyTable.Here here) {
        List<String> out = new ArrayList<>();
        int year = here.year();
        MoneyTable.Money main = here.money();
        Map<String, Long> money = purse.money();
        List<String> taken = new ArrayList<>();
        List<String> other = new ArrayList<>();
        long mainAmount = money.getOrDefault(main.id(), 0L);
        if (mainAmount > 0) {
            taken.add(named(main, mainAmount, year));
        }
        for (Map.Entry<String, Long> e : money.entrySet()) {
            if (e.getKey().equals(main.id())) {
                continue;
            }
            MoneyTable.Money m = table.money(e.getKey());
            String words = m == null ? e.getValue() + " (" + e.getKey() + ")" : named(m, e.getValue(), year);
            if (here.takes(e.getKey())) {
                taken.add(words);
            } else {
                other.add(words);
            }
        }
        if (taken.isEmpty() && other.isEmpty()) {
            out.add("Your purse is empty.");
        } else if (taken.isEmpty()) {
            out.add("You have nothing people here take.");
        } else {
            String work = mainAmount > 0 && !main.inDays() ? MoneyFormat.workFor(mainAmount, here.dayPay()) : "";
            out.add("Your purse: " + String.join("; ", taken) + (work.isEmpty() || taken.size() > 1 ? "" : ", " + work
                    + " here") + ".");
        }
        if (!other.isEmpty()) {
            out.add("Not taken here: " + String.join("; ", other) + ".");
        }
        out.add(moneyHere(table, here));
        if (!here.placeKnown()) {
            out.add("Your place on Earth is not known here, so this is the money of the year in general.");
        }
        return out;
    }

    /** "Money here: ..." with its units, a day's pay and what else is taken. */
    public static String moneyHere(MoneyTable table, MoneyTable.Here here) {
        int year = here.year();
        MoneyTable.Money main = here.money();
        if (main.barter()) {
            return "People here have no money: they swap goods, and a day's work is the measure.";
        }
        if (main.inDays()) {
            return "Money here: " + main.called() + ". Prices here are told in days of work.";
        }
        StringBuilder sb = new StringBuilder("Money here: ").append(main.called()).append('.');
        String units = MoneyFormat.units(main, year);
        if (!units.isEmpty()) {
            sb.append(' ').append(Character.toUpperCase(units.charAt(0))).append(units.substring(1)).append('.');
        }
        sb.append(" A day's work pays about ").append(MoneyFormat.amount(main, here.dayPay(), year)).append('.');
        List<String> also = new ArrayList<>();
        for (MoneyTable.Money m : here.accepts()) {
            String rate = rateText(table, main, m, year);
            also.add(m.called() + (rate.isEmpty() ? "" : " (" + rate + ")"));
        }
        if (!also.isEmpty()) {
            sb.append(" Also taken here: ").append(String.join("; ", also)).append('.');
        }
        return sb.toString();
    }

    /** "7 O.S. rupees = 6 Indian rupees", from the table's fixed rate; empty without one. */
    public static String rateText(MoneyTable table, MoneyTable.Money a, MoneyTable.Money b, int year) {
        long[] r = table.rate(a.id(), b.id(), year);
        if (r == null) {
            return "";
        }
        String left = MoneyFormat.amount(a, r[0], year);
        String right = MoneyFormat.amount(b, r[1], year);
        int ls = left.indexOf(' ');
        int rs = right.indexOf(' ');
        if (!a.shortName().isEmpty() && !b.shortName().isEmpty() && ls > 0 && rs > 0 && left.indexOf(' ', ls + 1) < 0
                && right.indexOf(' ', rs + 1) < 0) {
            return left.substring(0, ls) + " " + a.shortName() + left.substring(ls) + " = " + right.substring(0, rs)
                    + " " + b.shortName() + right.substring(rs);
        }
        return left + " (" + a.name() + ") = " + right + " (" + b.name() + ")";
    }

    /** An amount with its money's name: "1 rupee (Osmania sicca)"; days-of-work amounts name themselves. */
    public static String named(MoneyTable.Money m, long amount, int year) {
        String words = MoneyFormat.amount(m, amount, year);
        if (m.inDays() || namesItself(words, m.name())) {
            return words;
        }
        return words + " (" + m.name() + ")";
    }

    /**
     * Step 24: true when the amount already names the money ("3 manillas" is manillas, "29 cowries" is cowrie shells):
     * then "(manillas)" after it says nothing.
     */
    static boolean namesItself(String words, String name) {
        if (words == null || name == null || words.isBlank() || name.isBlank()) {
            return false;
        }
        String last = words.trim().substring(words.trim().lastIndexOf(' ') + 1).toLowerCase(java.util.Locale.ROOT);
        String first = name.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        return stem(last).equals(stem(first));
    }

    private static String stem(String w) {
        String s = w.replaceAll("[^a-z\\p{L}]", "");
        if (s.endsWith("ies") && s.length() > 4) {
            return s.substring(0, s.length() - 3) + "ie";
        }
        if (s.endsWith("es") && s.length() > 4 && !s.endsWith("ies")) {
            return s.substring(0, s.length() - 1);
        }
        return s.endsWith("s") && s.length() > 3 ? s.substring(0, s.length() - 1) : s;
    }

    /** One line for checks: what this purse holds. */
    public static String holdings(MoneyTable table, PlayerPurses.Purse purse, int year) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : purse.money().entrySet()) {
            MoneyTable.Money m = table.money(e.getKey());
            parts.add(m == null ? e.getValue() + " " + e.getKey() : named(m, e.getValue(), year));
        }
        return parts.isEmpty() ? "empty" : String.join("; ", parts);
    }

    /**
     * The purse's own check, with no world: typed amounts, amounts in words, the start money given once, a purse
     * that never goes below nothing, and a save read back the same. Null when all is well.
     */
    public static String selfTest() {
        MoneyTable table = MoneyTable.get();
        MoneyTable.Money os = table.money("osmania_sicca");
        if (os == null) {
            return "the money table has no Osmania sicca";
        }
        try {
            long annas = MoneyFormat.parse(os, "4", "annas", 1948);
            long rupees = MoneyFormat.parse(os, "1.5", "Rupees", 1948);
            long days = MoneyFormat.parse(os, "2", "days", 1948);
            long pai = MoneyFormat.parse(os, "-3", "", 1948);
            if (annas != 48 || rupees != 288 || days != 384 || pai != -3) {
                return "typed amounts: 4 annas = " + annas + ", 1.5 rupees = " + rupees + ", 2 days = " + days
                        + ", -3 = " + pai + " pai; expected 48, 288, 384, -3";
            }
            try {
                MoneyFormat.parse(os, "2", "denarii", 1948);
                return "\"2 denarii\" was taken as Osmania sicca";
            } catch (IllegalArgumentException expected) {
                // Right: not a unit of this money.
            }
        } catch (IllegalArgumentException e) {
            return "a typed amount was refused: " + e.getMessage();
        }
        String a = MoneyFormat.amount(os, 205, 1948);
        String zero = MoneyFormat.amount(os, 0, 1948);
        if (!a.equals("1 rupee 1 anna 1 pai") || !zero.equals("0 pai")) {
            return "205 pai reads \"" + a + "\" and 0 reads \"" + zero + "\"";
        }
        PlayerPurses purses = new PlayerPurses();
        UUID who = new UUID(0L, 19L);
        if (!purses.startIfNew(who, os.id(), 192, 19480914) || purses.startIfNew(who, os.id(), 192, 19480915)) {
            return "the start money was not given exactly once";
        }
        if (purses.add(who, os.id(), 48) != 240 || purses.add(who, os.id(), -300) != -1
                || purses.view(who).amount(os.id()) != 240) {
            return "putting in 48 and taking out 300 pai did not leave 240";
        }
        PlayerPurses back = PlayerPurses.load(purses.save(new CompoundTag()));
        PlayerPurses.Purse p = back.view(who);
        if (p.amount(os.id()) != 240 || !p.started() || p.startDate() != 19480914 || p.startAmount() != 192) {
            return "a saved purse came back as " + p.money() + " (started " + p.started() + ")";
        }
        back.reset(who);
        if (back.view(who).started() || !back.view(who).isEmpty()) {
            return "a reset purse is not empty";
        }
        return null;
    }
}
