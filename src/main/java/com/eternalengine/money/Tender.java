package com.eternalengine.money;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Step 24: paying from the two slots of the trade screen. Minecraft's trade screen has two slots to pay with, so a
 * payment is at most two kinds of things (two kinds of coins, two kinds of goods, or one of each), each at most a
 * stack. Of all the ways to cover a price this picks the one that hands over the least too much (the change, or what
 * a barter partner owes you), then the fewest pieces, then the bigger pieces. Pure Java (no game), for the checks.
 */
public final class Tender {

    private Tender() {
    }

    /**
     * One kind of thing that can pay: {@code count} of them, each worth {@code each} in the price's smallest unit (a
     * coin of another money converted, goods at what the buyer pays), at most {@code max} in one slot.
     */
    public record Kind(String key, double each, long count, int max) {
        /** What {@code k} of them are worth, rounded down (a buyer never pays for a part of a unit). */
        public long value(long k) {
            return (long) Math.floor(k * each + 1e-6);
        }

        long usable() {
            return Math.max(0L, Math.min(count, max));
        }
    }

    /** How many of kind {@code a} and of kind {@code b} (index into the list, -1 for none) and what they are worth. */
    public record Pick(int a, long na, int b, long nb, long value) {
        public static final Pick NOTHING = new Pick(-1, 0L, -1, 0L, 0L);

        public long pieces() {
            return na + nb;
        }

        /** How many of kind {@code i} this pick takes. */
        public long of(int i) {
            return (a == i ? na : 0L) + (b == i ? nb : 0L);
        }
    }

    /**
     * The best way to hand over at least {@code need} from at most two of the kinds: least too much, then fewest
     * pieces, then bigger pieces. {@link Pick#NOTHING} when nothing is needed; null when the kinds cannot cover it.
     */
    @Nullable
    public static Pick choose(List<Kind> kinds, long need) {
        if (need <= 0) {
            return Pick.NOTHING;
        }
        Pick best = null;
        int n = kinds.size();
        for (int i = 0; i < n; i++) {
            Kind ka = kinds.get(i);
            long maxA = ka.usable();
            if (ka.each() <= 0 || maxA <= 0) {
                continue;
            }
            long alone = least(ka, need);
            if (alone > 0 && alone <= maxA) {
                best = better(best, new Pick(i, alone, -1, 0L, ka.value(alone)), need, kinds);
            }
            for (int j = 0; j < n; j++) {
                Kind kb = kinds.get(j);
                long maxB = kb.usable();
                if (j == i || kb.each() <= 0 || maxB <= 0) {
                    continue;
                }
                for (long x = 1; x <= maxA; x++) {
                    long va = ka.value(x);
                    if (va >= need) {
                        break; // kind a alone covers it: counted above
                    }
                    long y = least(kb, need - va);
                    if (y > 0 && y <= maxB) {
                        best = better(best, new Pick(i, x, j, y, va + kb.value(y)), need, kinds);
                    }
                }
            }
        }
        return best;
    }

    /** The fewest of a kind worth at least {@code need} (0 when nothing is needed). */
    static long least(Kind k, long need) {
        if (need <= 0) {
            return 0L;
        }
        long n = Math.max(1L, (long) Math.ceil(need / k.each() - 1e-6));
        while (k.value(n) < need) {
            n++;
        }
        while (n > 1 && k.value(n - 1) >= need) {
            n--;
        }
        return n;
    }

    private static Pick better(@Nullable Pick best, Pick p, long need, List<Kind> kinds) {
        if (best == null) {
            return p;
        }
        long over = p.value() - need;
        long bestOver = best.value() - need;
        if (over != bestOver) {
            return over < bestOver ? p : best;
        }
        if (p.pieces() != best.pieces()) {
            return p.pieces() < best.pieces() ? p : best;
        }
        return biggest(kinds, p) > biggest(kinds, best) ? p : best;
    }

    private static double biggest(List<Kind> kinds, Pick p) {
        double e = 0.0d;
        if (p.a() >= 0) {
            e = Math.max(e, kinds.get(p.a()).each());
        }
        if (p.b() >= 0) {
            e = Math.max(e, kinds.get(p.b()).each());
        }
        return e;
    }

    /**
     * Its own check: 19 pies from a rupee note and two annas takes the two annas (5 pies change, not 173); from 1 anna,
     * 1 pice and 4 pies it is three kinds, so no two of them make it; 8 annas from 3 four-anna pieces takes two; an
     * exact pair wins over a big coin with change; 0 needs nothing; 100 pies come from two stacks of pies (64 + 36).
     * Null when all is well.
     */
    public static String selfTest() {
        Kind rupee = new Kind("rupee", 192, 1, 64);
        Kind anna = new Kind("anna", 12, 2, 64);
        Pick p = choose(List.of(rupee, anna), 19);
        if (p == null || p.of(0) != 0 || p.of(1) != 2 || p.value() != 24) {
            return "19 pies from a rupee and 2 annas should take the 2 annas: " + p;
        }
        Pick three = choose(List.of(new Kind("anna", 12, 1, 64), new Kind("pice", 3, 1, 64), new Kind("pie", 1, 4, 64)), 19);
        if (three != null) {
            return "19 pies from 1 anna, 1 pice and 4 pies should need three kinds: " + three;
        }
        Pick four = choose(List.of(new Kind("4 annas", 48, 3, 64)), 96);
        if (four == null || four.na() != 2 || four.value() != 96) {
            return "8 annas from 3 four-anna pieces: " + four;
        }
        Pick exact = choose(List.of(new Kind("note", 100, 1, 64), new Kind("20", 20, 3, 64), new Kind("10", 10, 1, 64)), 70);
        if (exact == null || exact.value() != 70) {
            return "70 from 20 + 20 + 20 + 10 should be exact, not the 100 note: " + exact;
        }
        if (choose(List.of(rupee), 0) != Pick.NOTHING) {
            return "nothing needed should take nothing";
        }
        Pick slot = choose(List.of(new Kind("pie", 1, 64, 64), new Kind("pie", 1, 136, 64)), 100);
        if (slot == null || slot.value() != 100 || slot.pieces() != 100) {
            return "100 pies from two stacks of pies: " + slot;
        }
        return null;
    }
}
