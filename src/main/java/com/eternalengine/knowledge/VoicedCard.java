package com.eternalengine.knowledge;

import com.eternalengine.story.BranchStory;
import com.eternalengine.timeline.Provenance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>G10, as a card rather than as a reading: one question, one day, more than one answer.</b>
 *
 * <h2>The defect this exists to fix</h2>
 *
 * <p>{@link KnowledgeCard.Question#at(int)} picks the last answer whose date has come. That is correct for a
 * timeline of one opinion, and it is exactly wrong for a place, because a place has several at once. Two
 * answers dated the same day do not merely disagree: one of them can never be heard at all, and nothing says
 * which one was dropped. {@link #shadowed()} reports those, so the pack can be checked for the fault instead of
 * quietly losing half of what it holds.
 *
 * <p>So this class keeps the card and adds <b>voices</b> beside it. Nothing in the pack format changes: a card
 * with no spoken answers behaves exactly as before and reads exactly as it did, and the reader says so out loud
 * ({@code "the card does not say who tells you this"}). A card with them holds as many answers as there are
 * voices, and every one of them carries who said it and what the claim rests on.
 *
 * <h2>Why the voices come from the story package</h2>
 *
 * <p>{@link BranchStory.Voice} is already the place where a narrator is defined with what it serves and what it
 * overstates, and a second definition of "a court chronicler" in this package would drift from the first. The
 * dependency runs one way: {@code knowledge} knows about {@code story}, and {@code story} knows nothing about
 * cards.
 *
 * <h2>What is a voice allowed to be asked</h2>
 *
 * <p>Level 1 is the street and level 2 is the court ({@link KnowledgeCard#level()}). All four voices can be
 * asked what the street knows, because all four have walked through it. A traveller passing through can be
 * asked about the market and cannot be asked what the court decided, so at level 2 three voices answer and the
 * traveller does not. That is a choice, not a discovery, and it is the kind of choice that is worth writing
 * down: see {@link #speaksTo}.
 */
public final class VoicedCard {

    /**
     * One thing one voice says about one question, and the years it says it.
     *
     * <p>A {@code null} voice is not an error: it is the anonymous answer this class exists to point at, the
     * one a card gives today with nobody's name on it. Keeping it as a value rather than hiding it means the
     * reader can be told, and the count of anonymous answers is a number the notes can quote.
     */
    public record Spoken(String questionId, int from, int to, BranchStory.Voice voice, String text,
                         String record, Provenance provenance) {

        public Spoken {
            questionId = questionId == null ? "" : questionId;
            text = text == null ? "" : text;
            record = record == null ? "" : record;
            provenance = provenance == null ? Provenance.OBSERVED : provenance;
        }

        public boolean liveOn(int date) {
            return from <= date && date <= to;
        }

        /** True when nobody is named as the source, which is every answer a card holds today. */
        public boolean anonymous() {
            return voice == null;
        }

        /**
         * The words as a reader gets them. The mark is only shown when there is something to mark: a voice
         * reading a row of the record is {@link Provenance#OBSERVED} and needs no warning, and a claim that is
         * somebody's own or only modelled says so every time.
         */
        public String line() {
            String mark = provenance == Provenance.OBSERVED ? "" : " (" + provenance.label() + ")";
            String who = anonymous() ? "nobody in particular" : voice.who();
            return who + ": " + text + mark;
        }

        /** The same words with the row behind them, for a probe or a debug screen. */
        public String sourced() {
            return line() + "  [" + record + "]";
        }
    }

    /** No end: a voice holds this until it is contradicted by a later line of its own. */
    public static final int OPEN = KnowledgeCard.OPEN;

    private final KnowledgeCard card;
    private final List<Spoken> spoken;

    private VoicedCard(KnowledgeCard card, List<Spoken> spoken) {
        this.card = card;
        this.spoken = List.copyOf(spoken);
    }

    public static VoicedCard of(KnowledgeCard card) {
        if (card == null) {
            throw new IllegalArgumentException("a voiced card needs a card");
        }
        return new VoicedCard(card, List.of());
    }

    /**
     * A voice reading a row of the record. {@link Provenance#OBSERVED}, because the words are a reading of
     * something the packs hold and not a second source.
     */
    public static Spoken recorded(String questionId, int from, int to, BranchStory.Voice voice, String text,
                                  String record) {
        return new Spoken(questionId, from, to, voice, text, record, Provenance.OBSERVED);
    }

    /**
     * A branch's own knowledge, attached to a question the card already asks. The words are
     * {@link BranchStory#authored} so that the sentence a player's claim makes is written in exactly one place.
     */
    public static Spoken authored(String questionId, int to, BranchStory.Sentence sentence) {
        return fromSentence(questionId, to, sentence);
    }

    /** A rumour beside it, likewise written where {@link BranchStory#rumour} writes it. */
    public static Spoken rumour(String questionId, int to, BranchStory.Sentence sentence) {
        return fromSentence(questionId, to, sentence);
    }

    /** Any sentence from a story, held as an answer to a question. The words are never rewritten here. */
    public static Spoken fromSentence(String questionId, int to, BranchStory.Sentence sentence) {
        if (sentence == null) {
            throw new IllegalArgumentException("a spoken answer needs a sentence");
        }
        return new Spoken(questionId, sentence.year(), to, sentence.voice(), sentence.text(), sentence.record(),
                sentence.provenance());
    }

    /**
     * The same card with one more voice on it. A voice answering a question the card does not ask is a mistake
     * in the caller rather than a fact about the world, so it fails loudly rather than being dropped.
     */
    public VoicedCard with(Spoken answer) {
        if (answer == null) {
            throw new IllegalArgumentException("no answer");
        }
        if (card.question(answer.questionId()) == null) {
            throw new IllegalArgumentException("this card has no question " + answer.questionId());
        }
        List<Spoken> next = new ArrayList<>(spoken);
        next.add(answer);
        next.sort(Comparator.comparingInt((Spoken s) -> s.voice() == null ? 99 : s.voice().ordinal())
                .thenComparingInt(Spoken::from));
        return new VoicedCard(card, next);
    }

    public KnowledgeCard card() {
        return card;
    }

    public List<Spoken> spoken() {
        return spoken;
    }

    public List<String> questionIds() {
        List<String> out = new ArrayList<>();
        for (KnowledgeCard.Question q : card.questions()) {
            out.add(q.id());
        }
        return out;
    }

    /**
     * Every answer to one question that is live on one day, in a fixed order, with the card's own anonymous
     * answer as the last resort.
     *
     * <p>The order is by voice and not by the order the caller added them, because a reader who is shown a
     * court account and then a traveller's should be shown them in the same order every time.
     */
    public List<Spoken> accounts(String questionId, int date) {
        KnowledgeCard.Question q = card.question(questionId);
        if (q == null) {
            throw new IllegalArgumentException("this card has no question " + questionId);
        }
        List<Spoken> out = new ArrayList<>();
        for (Spoken s : spoken) {
            if (s.questionId().equals(questionId) && s.liveOn(date)) {
                out.add(s);
            }
        }
        if (out.isEmpty()) {
            KnowledgeCard.Answer a = q.at(date);
            if (a != null) {
                out.add(new Spoken(questionId, a.from(), a.to(), null, a.text(), "the card itself",
                        Provenance.OBSERVED));
            }
        }
        return out;
    }

    /** True when one question has more than one live answer on one day. This is G10 in one boolean. */
    public boolean severalAccounts(String questionId, int date) {
        return accounts(questionId, date).size() > 1;
    }

    /** Every question of the card that has more than one live answer on the day, for a screen or a probe. */
    public List<String> disputed(int date) {
        List<String> out = new ArrayList<>();
        for (String qid : questionIds()) {
            if (severalAccounts(qid, date)) {
                out.add(qid);
            }
        }
        return out;
    }

    /**
     * The question as a reader gets it: what was asked, every live account with its name on it, and one honest
     * line about what the reader is looking at. Never just the winning answer.
     */
    public List<String> read(String questionId, int date) {
        KnowledgeCard.Question q = card.question(questionId);
        if (q == null) {
            throw new IllegalArgumentException("this card has no question " + questionId);
        }
        List<String> out = new ArrayList<>();
        out.add(q.label().isEmpty() ? q.id() : q.label());
        List<Spoken> live = accounts(questionId, date);
        for (Spoken s : live) {
            out.add("  " + s.line());
        }
        if (live.isEmpty()) {
            out.add("  Nothing on this is live on " + date + ": the record has nothing to say about it yet, "
                    + "which is not the same as saying nothing happened.");
            return out;
        }
        if (live.size() == 1 && live.get(0).anonymous()) {
            out.add("  One answer, and the card does not say who tells you this. That is the hole this step "
                    + "fills, and saying so is better than a name made up to fill it.");
            return out;
        }
        if (live.size() == 1) {
            out.add("  One account, from " + live.get(0).voice().who() + ". Where there is one account there is "
                    + "nothing to argue about and nothing to check it against.");
            return out;
        }
        Set<String> who = new LinkedHashSet<>();
        boolean allFromTheRecord = true;
        for (Spoken s : live) {
            who.add(s.anonymous() ? "an unnamed card" : s.voice().who());
            if (s.provenance() != Provenance.OBSERVED) {
                allFromTheRecord = false;
            }
        }
        String head = "  " + live.size() + " live accounts on one day: " + String.join(", ", who) + ". A card with "
                + "one answer per question could hold only one of them, and nothing would say which was dropped.";
        // The closing note has to know what it is looking at. Two voices over one row is a reading of the same
        // material; a claim and a rumour is not, and saying "readings of the same rows" about those would be a
        // worse lie than keeping quiet.
        out.add(allFromTheRecord
                ? head + " Where the record has one account these are readings of the same rows and not two sources."
                : head + " Not all of these are in the record: some are somebody's own account of it and some are "
                        + "what others say about it. The marks say which is which, and nothing here is the record's "
                        + "word.");
        return out;
    }

    public String asText(String questionId, int date) {
        return String.join("\n", read(questionId, date));
    }

    /**
     * <b>The fault in the base card, reported rather than repaired.</b> Answers sharing a start day cannot both
     * be reached: {@link KnowledgeCard.Question#at(int)} keeps the last of them and the others are unreachable
     * for every date, so the pack silently holds less than it appears to.
     *
     * @return one line per unreachable answer, empty when the card is clean
     */
    public List<String> shadowed() {
        List<String> out = new ArrayList<>();
        for (KnowledgeCard.Question q : card.questions()) {
            List<KnowledgeCard.Answer> as = q.answers();
            for (int i = 0; i < as.size(); i++) {
                for (int j = i + 1; j < as.size(); j++) {
                    if (as.get(i).from() == as.get(j).from()) {
                        out.add("question " + q.id() + ": an answer starting " + as.get(i).from()
                                + " is unreachable, because another starts the same day and the later one wins");
                    }
                }
            }
        }
        return out;
    }

    /** How many of the card's answers have nobody's name on them, which today is all of them. */
    public int anonymousAnswers(int date) {
        int n = 0;
        for (String qid : questionIds()) {
            List<Spoken> live = accounts(qid, date);
            if (live.size() == 1 && live.get(0).anonymous()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Can this voice be asked a question at this level? Level 1 is the street and everyone has walked through
     * it. Level 2 is the court, and a traveller passing through was not in the room.
     */
    public static boolean speaksTo(BranchStory.Voice voice, int level) {
        if (voice == null) {
            return false;
        }
        if (level <= 1) {
            return true;
        }
        return voice != BranchStory.Voice.TRAVELLER;
    }

    /** The voices that can be asked a question at this level, in the order the enum declares them. */
    public static List<BranchStory.Voice> voicesFor(int level) {
        List<BranchStory.Voice> out = new ArrayList<>();
        for (BranchStory.Voice v : BranchStory.Voice.values()) {
            if (speaksTo(v, level)) {
                out.add(v);
            }
        }
        return out;
    }

    /** One line for a screen, saying what a card here can now do. */
    public static String ruleLine() {
        return "A card can hold more than one answer to one question on one day, and each answer says who "
                + "gives it and what it rests on. Where it holds only one, the reader is told that nobody is "
                + "named.";
    }
}
