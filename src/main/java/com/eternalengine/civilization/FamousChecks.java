package com.eternalengine.civilization;

import java.util.List;

/**
 * Step 41 (D11 famous people where and when they were, C11 families and dynasties): the checks for
 * the {@code famous} part of {@code /ee check}.
 *
 * <p>These live here, next to the two registers they check, rather than inside
 * {@code HistoryChecks}, for the same reason step 40's did: {@code HistoryChecks} reaches into half
 * the mod and cannot be compiled without Minecraft on the classpath, so a check written only there
 * would go unexercised until somebody built the mod. Here it depends on nothing but {@link Presence}
 * and {@link Kinship}, which means:
 *
 * <ul>
 * <li>{@code /ee check all} runs it inside a real server;</li>
 * <li>and {@code worldfill/probes/FamousStep41Probe.java} runs <b>the very same code</b> outside
 *     one, in the sandbox, every time the probe runs.</li>
 * </ul>
 *
 * <p>So a claim cannot pass in the build and fail in the game. The ones it holds the step to are
 * that <b>a person is where he was and not only where he was born</b> — SYSTEMS_PLAN line 507, and
 * the whole reason for D11 — that <b>a family is blood and not a list of card links</b>, that
 * <b>a dynasty is derived and not asserted</b>, and that <b>the register declines rather than
 * invents</b> when the record does not say.
 */
public final class FamousChecks {

    private FamousChecks() {}

    /** Where the results go. {@code HistoryChecks.Section} fills one of these; so does the probe. */
    public interface Sink {
        void check(boolean ok, String text, String whyNot);
        void known(String text, String detail);
        void info(String text);
    }

    /** T. H. White: born in Bombay, read at Queens' College, Cambridge, died in Piraeus. */
    public static final int WHITE = 453410;
    public static final double CAMBRIDGE_LAT = 52.202d;
    public static final double CAMBRIDGE_LON = 0.115d;
    public static final int CAMBRIDGE_YEAR = 1928;
    public static final double HERE_KM = 30.0d;

    /**
     * Famous people at their homes, and their families (step 41), asked inside a running server the
     * same questions the probe asks outside it.
     */
    public static void run(Sink sink) {
        Presence pres = Presence.get();
        Kinship kin = Kinship.get();

        // ---------------------------------------------------------------- the two registers load
        sink.check(pres.hasData(), "the presence register loaded", pres.source());
        sink.check(kin.hasData(), "the kinship register loaded", kin.source());
        if (!pres.hasData() || !kin.hasData()) {
            return;
        }
        sink.info("presence: " + pres.rows() + " stays over " + pres.placeCount() + " places; "
                + "kinship: " + kin.edgeCount() + " parent edges, " + kin.marriageCount()
                + " marriages, " + kin.relativeCount() + " relatives, " + kin.houseCount()
                + " houses, " + kin.dynasties().size() + " dynasties");

        // ---------------------------------------------------------------- SYSTEMS_PLAN line 507
        List<Presence.Stay> here = pres.hereIn(CAMBRIDGE_LAT, CAMBRIDGE_LON, CAMBRIDGE_YEAR, HERE_KM);
        List<Presence.Stay> born = pres.bornNear(CAMBRIDGE_LAT, CAMBRIDGE_LON, CAMBRIDGE_YEAR, HERE_KM);
        boolean white = false;
        for (Presence.Stay s : here) {
            white |= s.qid() == WHITE;
        }
        sink.check(white, "T. H. White is in Cambridge in 1928, though he was born in Bombay",
                "the register does not put him there");
        sink.check(born.size() < here.size(),
                "who is here is not the same list as who is from here",
                born.size() + " born here against " + here.size() + " here, at Cambridge in 1928");
        sink.known("at Cambridge in " + CAMBRIDGE_YEAR + ": " + here.size()
                + " people with a card were there, and only " + born.size() + " of them were born there",
                "the six-against-three-hundred-and-fifty-two gap is what the mod could say before this step");

        List<Presence.Stay> whites = pres.of(WHITE);
        sink.check(whites.size() >= 3, "his register holds more than a birthplace",
                whites.size() + " stays");

        // ---------------------------------------------------------------- a stay that says why
        long dated = 0;
        long estimated = 0;
        long future = 0;
        long longSpan = 0;
        for (Presence.Stay s : whites) {
            if (s.estimated()) {
                estimated++;
            } else {
                dated++;
            }
            for (int y : new int[] {s.from(), s.to()}) {
                // Beyond the last recorded year there are no famous people - the record stops,
                // it does not continue. That is a different thing from "not yet born", and the
                // ceiling is the world's span, not the record's.
                if (y != Presence.UNKNOWN
                        && y > com.eternalengine.clock.Timeline.RECORDED_UNTIL) {
                    future++;
                }
            }
            if (s.from() != Presence.UNKNOWN && s.to() != Presence.UNKNOWN && s.to() - s.from() > 120) {
                longSpan++;
            }
        }
        sink.check(future == 0, "no stay ends in the far future: a year nobody was alive in is not a date",
                future + " of his do");
        sink.check(longSpan == 0, "no stay spans longer than a life",
                longSpan + " of his do");
        sink.known("T. H. White: " + whites.size() + " stays, " + dated + " dated and "
                + estimated + " dated only by his life", join(whites));

        // ---------------------------------------------------------------- families, from blood
        sink.check(kin.edgeCount() > 90000, "the family graph is big enough to be a family register",
                kin.edgeCount() + " parent edges");
        sink.check(kin.houseCount() > 3000, "and the houses are there", kin.houseCount() + " houses");
        boolean anyFamily = false;
        for (int qid : new int[] {9682, 1001, 3044, 9439, 9129}) {
            if (!kin.parentsOf(qid).isEmpty() || !kin.childrenOf(qid).isEmpty()
                    || !kin.spousesOf(qid).isEmpty()) {
                anyFamily = true;
                break;
            }
        }
        sink.check(anyFamily, "a person with a card has parents, children or a spouse in the record",
                "none of five well-known people has any");
        sink.known("Elizabeth II: " + kin.parentsOf(9682).size() + " parents, "
                + kin.childrenOf(9682).size() + " children, " + kin.spousesOf(9682).size()
                + " marriages in the record", "from blood, and not from the links on her card");

        // ---------------------------------------------------------------- dynasties, derived
        List<Kinship.Dynasty> dyn = kin.dynasties();
        sink.check(dyn.size() > 400, "the dynasties are there", dyn.size() + " dynasties");
        boolean small = false;
        boolean unnamed = false;
        boolean flat = false;
        for (Kinship.Dynasty d : dyn) {
            small |= d.size() < 3;
            unnamed |= d.name().isBlank();
            flat |= d.generations() < 1;
        }
        sink.check(!small, "no dynasty has fewer than three holders: a component of two is a coincidence",
                "at least one has two or fewer");
        sink.check(!unnamed, "every dynasty has a name to print", "at least one is called nothing at all");
        sink.check(!flat, "every dynasty spans at least one generation", "at least one spans none");
        boolean ottoman = false;
        boolean wittelsbach = false;
        for (Kinship.Dynasty d : dyn) {
            String n = d.name().toLowerCase();
            ottoman |= n.contains("ottoman");
            wittelsbach |= n.contains("wittelsbach");
        }
        sink.check(ottoman, "the Ottoman sultans are one of them, derived and not asserted",
                "they are not among the " + dyn.size());
        sink.check(wittelsbach, "and so are the Wittelsbach dukes of Bavaria",
                "they are not among the " + dyn.size());

        // ---------------------------------------------------------------- a register that declines
        int nobody = 999999999;
        sink.check(!kin.kinsman(nobody).named(), "a person the record does not name is not invented",
                "the register invented one: " + kin.kinsman(nobody).name());
        sink.check(kin.parentsOf(nobody).isEmpty(), "and he has no parents in the record",
                "it gave him " + kin.parentsOf(nobody).size());
        sink.check(pres.of(nobody).isEmpty(), "and nowhere he has been",
                "it put him in " + pres.of(nobody).size() + " places");
    }

    private static String join(List<Presence.Stay> stays) {
        StringBuilder sb = new StringBuilder();
        for (Presence.Stay s : stays) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(s.line());
        }
        return sb.toString();
    }
}
