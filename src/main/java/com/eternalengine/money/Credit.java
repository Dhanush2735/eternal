package com.eternalengine.money;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 44 part 8 (E19): what it costs to borrow, who lends, and whether it is lawful.
 *
 * <p>The survey for this step found nothing at all: no bank, no lender, no loan, no interest and
 * no bill of exchange anywhere in the mod. The money-changer was there, changing coin at a fee,
 * which is a bank's oldest function with the lending left out.
 *
 * <p>So this is a register rather than an engine: 26 dated rows saying what credit cost, who
 * offered it, and what the law had to say about it, each marked <b>recorded</b> (a document
 * states it), <b>measured</b> (read off many records, like the 1,563 cash waqfs of Bursa) or
 * <b>estimated</b>. The mark is shown to the player, because the Ottoman and English figures are
 * solid enough to lean on and the African and Andean ones deliberately are not.
 *
 * <p>Two things this class refuses to do.
 *
 * <p><b>It will not fill a silence with a number.</b> Five rows carry no rate at all, and that is
 * the finding rather than the absence of one: pre-colonial West Africa had money, savings, deposit
 * and credit without a <i>price</i> for credit — the return was a place in the rotation, or a
 * pledge, or a share of the crop — and the Inca, who kept the most careful accounts in the
 * Americas, had no money whatsoever. A player who asks the interest rate in Cusco in 1500 must be
 * told there was not one.
 *
 * <p><b>It treats a ban on interest as a description of credit, not the absence of it.</b> Where
 * interest is not lawful the loan was restructured rather than abandoned: the Ottomans ran cash
 * waqfs, Islamic lenders sell cost-plus and use legal stratagems, Buddhist monasteries in China
 * ran pawnshops, and Italian bills of exchange hid the interest in the exchange rate. Every row
 * that says interest is forbidden also says what was done instead, because "no lending here" is
 * the one answer that is certainly wrong.
 */
public final class Credit {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/credit.json.gz";

    private static volatile Credit instance;

    /**
     * What borrowing costs, and the terms around it.
     *
     * @param id       which row of the register this is
     * @param low      the lowest rate recorded, per cent a year
     * @param high     the highest
     * @param kind     what is borrowed against: silver, grain, a cargo, land, a pledge
     * @param lender   who does the lending
     * @param lawful   whether charging interest is permitted; {@code "capped"} when it is
     *                 permitted up to a legal maximum
     * @param instead  what is done where interest is not lawful, or where there is no price for
     *                 credit at all
     * @param mark     recorded, measured or estimated
     * @param note     what the number means
     * @param noRate   true when there is no rate and that is a finding
     */
    public record Loan(String id, double low, double high, String kind, String lender,
                       String lawful, String instead, String mark, String note, boolean noRate) {

        /** "twelve to thirty", "twenty", or null when there is no rate. */
        @Nullable
        public String rate() {
            if (noRate) {
                return null;
            }
            if (Math.abs(high - low) < 0.005d) {
                return trim(low);
            }
            return trim(low) + " to " + trim(high);
        }

        private static String trim(double v) {
            return Math.abs(v - Math.round(v)) < 0.005d
                    ? String.valueOf(Math.round(v))
                    : String.format(Locale.ROOT, "%.2f", v);
        }

        /** True when charging interest is forbidden outright. */
        public boolean forbidden() {
            return "false".equals(lawful);
        }

        /** True when interest is permitted but capped by law. */
        public boolean capped() {
            return "capped".equals(lawful);
        }
    }

    /** What borrowing costs at a place in a year. */
    public record Terms(ProductAtlas.Place place, List<Loan> loans) {

        /** The one to quote: measured first, then recorded, then estimated. */
        @Nullable
        public Loan best() {
            return loans.isEmpty() ? null : loans.get(0);
        }

        /** True when nothing in the register reaches this place and year. */
        public boolean silent() {
            return loans.isEmpty();
        }
    }

    private final List<Loan> rows;
    private final List<double[]> boxes;
    private final List<int[]> years;
    private final List<String> kinds;
    private final Map<String, String> kindWords;
    private final Map<String, String> lenderWords;
    private final String problem;

    private Credit(List<Loan> rows, List<double[]> boxes, List<int[]> years, List<String> kinds,
                   Map<String, String> kindWords, Map<String, String> lenderWords, String problem) {
        this.rows = rows;
        this.boxes = boxes;
        this.years = years;
        this.kinds = kinds;
        this.kindWords = kindWords;
        this.lenderWords = lenderWords;
        this.problem = problem;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static Credit get() {
        Credit c = instance;
        if (c == null) {
            synchronized (Credit.class) {
                c = instance;
                if (c == null) {
                    c = load();
                    instance = c;
                }
            }
        }
        return c;
    }

    /** Forget the pack and read it again. */
    public static Credit reload() {
        synchronized (Credit.class) {
            instance = load();
            return instance;
        }
    }

    private static Credit load() {
        List<Loan> rows = new ArrayList<>();
        List<double[]> boxes = new ArrayList<>();
        List<int[]> years = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        Map<String, String> kindWords = new LinkedHashMap<>();
        Map<String, String> lenderWords = new LinkedHashMap<>();
        try (InputStream raw = Credit.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Credit(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(),
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (root.has("kindWords")) {
                for (Map.Entry<String, JsonElement> e
                        : root.getAsJsonObject("kindWords").entrySet()) {
                    kindWords.put(e.getKey(), e.getValue().getAsString());
                }
            }
            if (root.has("lenderWords")) {
                for (Map.Entry<String, JsonElement> e
                        : root.getAsJsonObject("lenderWords").entrySet()) {
                    lenderWords.put(e.getKey(), e.getValue().getAsString());
                }
            }
            if (root.has("kinds")) {
                for (JsonElement e : root.getAsJsonArray("kinds")) {
                    kinds.add(e.getAsString());
                }
            }
            if (root.has("rows")) {
                JsonArray arr = root.getAsJsonArray("rows");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    boolean noRate = o.get("low").isJsonNull() || o.get("high").isJsonNull();
                    rows.add(new Loan(o.get("id").getAsString(),
                            noRate ? 0 : o.get("low").getAsDouble(),
                            noRate ? 0 : o.get("high").getAsDouble(),
                            o.get("kind").getAsString(), o.get("lender").getAsString(),
                            o.get("lawful").isJsonNull() ? "unknown" : o.get("lawful").getAsString(),
                            o.get("instead").isJsonNull() ? null : o.get("instead").getAsString(),
                            o.get("mark").getAsString(), o.get("note").getAsString(), noRate));
                    JsonArray b = o.getAsJsonArray("boxes").get(0).getAsJsonArray();
                    boxes.add(new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                            b.get(2).getAsDouble(), b.get(3).getAsDouble()});
                    years.add(new int[]{o.get("from").getAsInt(), o.get("to").getAsInt()});
                }
            }
            return new Credit(rows, boxes, years, kinds, kindWords, lenderWords, null);
        } catch (Exception e) {
            return new Credit(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(),
                    "read of " + RESOURCE + " failed: " + e);
        }
    }

    /** Why the pack cannot be read, or null when it read cleanly. */
    @Nullable
    public String problem() {
        return problem;
    }

    public boolean loaded() {
        return problem == null && !rows.isEmpty();
    }

    public int count() {
        return rows.size();
    }

    /** The name of a kind of loan: "a loan on a ship's cargo". */
    public String kindWord(String kind) {
        return kindWords.getOrDefault(kind, kind);
    }

    /** Who lends, in a sentence: "the ṣarrāf", "the temple". */
    public String lenderWord(String lender) {
        return lenderWords.getOrDefault(lender, lender);
    }

    public String summary() {
        long rated = rows.stream().filter(r -> !r.noRate()).count();
        return rows.size() + " rows (" + rated + " with a rate, "
                + (rows.size() - rated) + " recording that there was none)"
                + (loaded() ? "" : " (" + problem + ")");
    }

    // -------------------------------------------------------------------------------------------------- the answer

    /**
     * What borrowing costs at a point in a year.
     *
     * <p>Which row is quoted first matters, and four things decide it, in this order.
     *
     * <ol>
     *   <li><b>A row with a rate beats one without.</b> If a player asks what borrowing costs in
     *       Tang Chang'an, the answer is the monastery's twelve to thirty-six per cent, not the
     *       fact that flying money existed. The five no-rate rows still surface — they are the only
     *       row for their place and time, so nothing outranks them.</li>
     *   <li><b>What a borrower pays beats a specialist instrument.</b> The discount on a hundi in
     *       Mughal India was one to eight per cent; a loan from the same ṣarrāf cost twelve to
     *       thirty. The bill is the more precisely dated row and it is the wrong answer, so the
     *       loan outranks it.</li>
     *   <li><b>Measured beats recorded beats estimated.</b> A rate read off 1,563 Bursa waqfs is a
     *       better answer than one inferred from the literature, however precisely the inferred
     *       one is dated.</li>
     *   <li><b>The narrower box.</b> A row about the Low Countries beats one about all of Europe.</li>
     *   <li><b>The narrower date range.</b> This is what separates Old Babylonian silver at twenty
     *       per cent from grain in the same city at thirty-three and a third: both are recorded,
     *       both cover Ur, so the four-century grain row wins over the fifteen-century silver
     *       one for a year inside both.</li>
     * </ol>
     */
    public Terms at(double lat, double lon, int year) {
        ProductAtlas.Place place = ProductAtlas.get().place(lat, lon, year);
        List<int[]> found = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            double[] b = boxes.get(i);
            int[] y = years.get(i);
            if (year >= y[0] && year <= y[1] && lat >= b[0] && lat <= b[1]
                    && lon >= b[2] && lon <= b[3]) {
                found.add(new int[]{i, (int) ((b[1] - b[0]) * (b[3] - b[2]))});
            }
        }
        found.sort(Comparator.<int[]>comparingInt(a -> rank(rows.get(a[0])))
                .thenComparingInt(a -> kindTier(rows.get(a[0]).kind()))
                .thenComparingInt(a -> a[1])
                .thenComparingInt(a -> years.get(a[0])[1] - years.get(a[0])[0]));
        List<Loan> out = new ArrayList<>();
        for (int[] f : found) {
            out.add(rows.get(f[0]));
        }
        return new Terms(place, out);
    }

    private static int rank(Loan l) {
        int byRate = l.noRate() ? 1 : 0;
        int byMark = switch (l.mark()) {
            case "measured" -> 0;
            case "recorded" -> 1;
            default -> 2;
        };
        return byRate * 3 + byMark;
    }

    /**
     * Not every row answers the same question, and mixing them gives a wrong number. The discount
     * on a hundi — one to eight per cent in Mughal India — is what a merchant paid to move a bill,
     * not what a borrower paid for a loan, which was twelve to thirty. Asked what credit costs,
     * the loan wins and the bill is context. So: what a borrower actually pays first, then a
     * specialist instrument, then a loan to a state.
     */
    private static int kindTier(String kind) {
        return switch (kind) {
            case "commercial", "silver", "grain", "mortgage", "pawn" -> 0;
            case "maritime", "deposit", "rosus", "none" -> 1;
            default -> 2; // a loan to a government
        };
    }

    /**
     * The terms as a sentence.
     *
     * <p>Three shapes, and the second is the one that had to be argued for. Where a row records
     * that credit had no price, the answer is not "nothing is known" — it is what was done
     * instead, which is the interesting half.
     */
    public static String sentence(Terms terms) {
        if (terms == null || terms.silent()) {
            return "nothing in the credit register reaches this place and year, so the mod will "
                    + "not say what borrowing costs here";
        }
        Loan l = terms.best();
        if (l.noRate()) {
            String what = l.instead() == null ? l.note() : l.instead();
            return "there is no price for credit here: " + what;
        }
        StringBuilder b = new StringBuilder();
        b.append(lenderWordStatic(l.lender())).append(" lends at ").append(l.rate())
                .append(" per cent a year on ").append(kindWordStatic(l.kind()))
                .append(" (").append(l.mark()).append(')');
        if (l.forbidden()) {
            b.append(". Charging interest is forbidden here");
            if (l.instead() != null) {
                b.append(", and lending goes on all the same: ").append(l.instead());
            }
        } else if (l.capped()) {
            b.append(", and that is the lawful maximum: a lender who takes more loses the debt");
        }
        return b.toString();
    }

    private static String kindWordStatic(String kind) {
        Credit c = instance;
        return c == null ? kind : c.kindWord(kind);
    }

    private static String lenderWordStatic(String lender) {
        Credit c = instance;
        return c == null ? lender : c.lenderWord(lender);
    }

    /** Every row in the pack, for a listing command. */
    public List<Loan> rows() {
        return List.copyOf(rows);
    }
}
