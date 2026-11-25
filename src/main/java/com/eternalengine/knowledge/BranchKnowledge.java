package com.eternalengine.knowledge;

import com.eternalengine.story.BranchStory;
import com.eternalengine.timeline.Provenance;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>G5: knowledge one timeline has that the record does not.</b>
 *
 * <h2>What a branch knows, and what it may not touch</h2>
 *
 * <p>A branch is a timeline that diverged (step 49). When it diverges, somebody in it learns something the
 * record never had: a player who brought an invention forward, a ruler who chose the other way, a rumour that
 * started because of what did not happen. That knowledge has to live <b>somewhere</b>, and the one place it may
 * not live is the record.
 *
 * <p>So a claim here is never {@link Provenance#OBSERVED}. It is either {@link Provenance#AUTHORED}, because a
 * person in that branch says it and their name is on it, or {@link Provenance#MODELLED}, because somebody repeats
 * it and nobody has checked. The record's own rows are what they were; the branch adds a voice, and the voice
 * says whose it is.
 *
 * <h2>Isolation, which is the whole point</h2>
 *
 * <p>A {@code BranchKnowledge} belongs to exactly one branch id and holds only that branch's claims. Two
 * branches of the same year, from the same point of divergence, do not share a claim, because they are not the
 * same timeline: one of them contains the invention and the other does not. {@link #attach} is the only way a
 * claim reaches a reader, and it attaches to <b>this</b> branch's card and nothing else.
 *
 * <p>Where a claim answers a question the card does not ask, it is not silently dropped: it comes back in
 * {@link Attached#unattached()} so a caller can see that the pack and the branch have drifted apart.
 */
public final class BranchKnowledge {

    /** One thing this branch knows, whose question it answers, and who says it. */
    public record Claim(String branch, int year, String question, String author,
                         BranchStory.Sentence sentence) {

        public Claim {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("a claim has to answer some question");
            }
            if (sentence == null) {
                throw new IllegalArgumentException("a claim needs a sentence");
            }
        }

        public boolean isRumour() {
            return sentence.provenance() == Provenance.MODELLED;
        }

        /** True when this came from the record itself, which no claim of a branch ever does. */
        public boolean inTheRecord() {
            return sentence.provenance() == Provenance.OBSERVED;
        }

        public String line() {
            return year + ": " + sentence.text() + " (" + sentence.provenance().label() + ", "
                    + (isRumour() ? "nobody's account in particular" : author + "'s") + ")";
        }
    }

    /** A card with this branch's claims on it, and the claims it could not place. */
    public record Attached(VoicedCard card, List<String> unattached) {
    }

    private final String branch;
    private final List<Claim> claims;

    private BranchKnowledge(String branch, List<Claim> claims) {
        this.branch = branch;
        this.claims = List.copyOf(claims);
    }

    public static BranchKnowledge of(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("a branch needs an id: knowledge with no branch belongs to nobody");
        }
        return new BranchKnowledge(branch, List.of());
    }

    public String branch() {
        return branch;
    }

    /**
     * Something a person in this branch knows and the record does not. The words come from
     * {@link BranchStory#authored}, so the sentence is written in one place only.
     */
    public BranchKnowledge claim(int year, String question, String text, String author,
                                 BranchStory.Voice voice) {
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("an authored claim has to name its author");
        }
        BranchStory.Sentence s = BranchStory.authored(year, text, author, voice);
        List<Claim> next = new ArrayList<>(claims);
        next.add(new Claim(branch, year, question, author, s));
        return new BranchKnowledge(branch, next);
    }

    /** What others in this branch say about it, which is not the same thing and is not authored. */
    public BranchKnowledge hearsay(int year, String question, String subject, BranchStory.Voice voice) {
        BranchStory.Sentence s = BranchStory.rumour(year, subject, voice);
        List<Claim> next = new ArrayList<>(claims);
        next.add(new Claim(branch, year, question, "", s));
        return new BranchKnowledge(branch, next);
    }

    public List<Claim> claims() {
        return claims;
    }

    public List<Claim> on(String question) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims) {
            if (c.question().equals(question)) {
                out.add(c);
            }
        }
        return out;
    }

    public List<Claim> by(String author) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims) {
            if (c.author().equals(author)) {
                out.add(c);
            }
        }
        return out;
    }

    public boolean knows(String question) {
        return !on(question).isEmpty();
    }

    public boolean empty() {
        return claims.isEmpty();
    }

    /** How many claims rest on nothing that can be checked, for a note that would rather be honest than neat. */
    public int unverified() {
        int n = 0;
        for (Claim c : claims) {
            if (c.isRumour()) n++;
        }
        return n;
    }

    /**
     * This branch's knowledge, on a card. Only claims of this branch are attached, because only this branch's
     * object is asked; a second branch is a second {@code BranchKnowledge} and shares nothing with this one.
     */
    public Attached attach(VoicedCard card) {
        if (card == null) {
            throw new IllegalArgumentException("nothing to attach to");
        }
        VoicedCard out = card;
        List<String> lost = new ArrayList<>();
        for (Claim c : claims) {
            if (out.card().question(c.question()) == null) {
                lost.add(c.question() + " (" + c.year() + "): this card does not ask that question");
                continue;
            }
            out = out.with(VoicedCard.fromSentence(c.question(), KnowledgeCard.OPEN, c.sentence()));
        }
        return new Attached(out, lost);
    }

    /** The branch's own account of what it knows, in order, for a book or a screen. */
    public List<String> trail() {
        List<String> out = new ArrayList<>();
        for (Claim c : claims) {
            out.add(c.line());
        }
        return out;
    }

    /** One line, for a screen, that says what this knowledge is and is not. */
    public String ruleLine() {
        if (claims.isEmpty()) {
            return "This branch knows nothing the record does not: nothing has come to it that the world did not "
                    + "already have.";
        }
        return "This branch knows " + claims.size() + " thing" + (claims.size() == 1 ? "" : "s") + " the record "
                + "does not, and " + (unverified() == 0 ? "none of it" : unverified() + " of them")
                + " is checked by anybody. None of it is written into the record: it belongs to "
                + branch + " and to no other timeline.";
    }
}
