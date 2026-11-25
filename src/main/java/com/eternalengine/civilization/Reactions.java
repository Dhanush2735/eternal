package com.eternalengine.civilization;

/**
 * Step 42 (D13 reactions, D18 friendship and greetings): <b>what a person here says to you</b>.
 *
 * <p>D13 names three things a person reacts to: <b>the player</b>, <b>the law</b>, and
 * <b>strange clothes and things from the future</b>. D18 names one more: that he
 * <b>remembers you, greets you by name, and keeps an opinion of you</b>. All four are words, not
 * numbers, so this register is a book of lines and the rules for choosing between them.
 *
 * <p>It is deliberately free of Minecraft: the saved state that holds an opinion lives in
 * {@code villager/PlayerStanding}, and this class holds only the choosing. That way the probe runs
 * the very same code the game runs, which is the rule this project has kept since step 40.
 *
 * <h3>What was there before</h3>
 *
 * <p>A reaction could only come from {@code ReputationEngine}, which holds 8 positive and 13
 * negative dimensions with no place and no year: one global number per player, so a man in 3000 BC
 * and a man in 1900 react to you identically, and nobody reacts to what you are carrying. And no
 * one greeted you at all - {@code PeopleMemory} remembers 64 people per place and holds no opinion
 * of anybody.
 */
public final class Reactions {

    private Reactions() {}

    /** How warm a reaction is, for callers that need to do something and not only say something. */
    public static final int HOSTILE = -2;
    public static final int WARY = -1;
    public static final int NEUTRAL = 0;
    public static final int WARM = 1;
    public static final int DELIGHTED = 2;

    /** The standing a stranger starts with: nobody here has heard of you either way. */
    public static final int STRANGER = 0;

    // ------------------------------------------------------------------ D18: the greeting

    /**
     * What a person here says when he sees you.
     *
     * <p>Three things shape it: <b>whether he knows you</b>, <b>what he has heard</b>, and - from
     * D7 - <b>whether he may open a conversation with you at all</b>. A villein of the third estate
     * does not greet a lord of the first; he waits to be spoken to, and says so. Everywhere in the
     * mod until now, everybody greeted everybody.
     *
     * @param standing -100 to 100, how well this place thinks of you
     * @param name     your name, as he knows it (null when he does not know it yet)
     * @param greeter  the order of the man speaking, or null when nobody in particular is
     * @param player   the order you are taken to stand in, or null when that is not known -
     *                 and when it is not known the register will not invent a slight
     * @return the line, never empty
     */
    public static String greet(int standing, String name, SocialOrder.Stratum greeter,
                               SocialOrder.Stratum player, double lat, double lon, int year) {
        SocialOrder.Order order = SocialOrder.get().hereAt(lat, lon, year);
        boolean known = standing != STRANGER;
        String who = name == null || name.isBlank() ? "you" : name;

        // D7's own rule, asked and not re-derived: a villein of the third estate does not open a
        // conversation with a lord of the first, whatever his opinion of you.
        if (!order.mayAddress(greeter, player)) {
            if (standing <= -40) {
                return "He looks down and says nothing. " + order.speak();
            }
            if (known && standing > 0) {
                return "He waits to be spoken to, but his face says he knows you. "
                        + order.addressLine(greeter, player) + ".";
            }
            return "He waits to be spoken to. " + order.addressLine(greeter, player) + ".";
        }

        if (!known) {
            return "You're not from here. " + order.speak();
        }
        if (standing <= -60) {
            return who + ". " + (order.isDefault()
                    ? "You're not wanted here."
                    : "The whole " + order.name() + " knows what you did.");
        }
        if (standing <= -20) {
            return who + ". I've heard things.";
        }
        if (standing < 20) {
            return who + ".";
        }
        if (standing < 60) {
            return who + "! It's been a while.";
        }
        return who + "! Come in, come in - " + order.name() + " has been talking about you.";
    }

    /** The greeting of a man who has never seen you: the same rule, with no standing to go on. */
    public static String greetStranger(double lat, double lon, int year) {
        return greet(STRANGER, null, null, null, lat, lon, year);
    }

    /** As {@link #greet} when nobody in particular is speaking and your rank here is not known. */
    public static String greet(int standing, String name, double lat, double lon, int year) {
        return greet(standing, name, null, null, lat, lon, year);
    }

    // ------------------------------------------------------------------ D13 (1): to the player

    /** How warm it is, as a number, for code that has to act and not only speak. */
    public static int warmth(int standing) {
        if (standing <= -60) {
            return HOSTILE;
        }
        if (standing <= -20) {
            return WARY;
        }
        if (standing < 20) {
            return NEUTRAL;
        }
        if (standing < 60) {
            return WARM;
        }
        return DELIGHTED;
    }

    /**
     * D13: how people here react to <b>you</b>.
     *
     * <p>The number is the reputation engine's; the words are this place's, because a Breton
     * peasant and a Boston merchant do not show the same feeling in the same way.
     */
    public static String toPlayer(int standing, double lat, double lon, int year) {
        SocialOrder.Order order = SocialOrder.get().hereAt(lat, lon, year);
        return switch (warmth(standing)) {
            case HOSTILE -> "They shut their doors. " + order.speak();
            case WARY -> "They watch you and say little. " + order.speak();
            case NEUTRAL -> "They answer what you ask and volunteer nothing. " + order.speak();
            case WARM -> "They make room for you. " + order.speak();
            default -> "They come out to meet you. " + order.speak();
        };
    }

    // ------------------------------------------------------------------ D13 (2): to the law

    /**
     * D13: how people here react to <b>the law</b> - which is to say, what the law here has just
     * done to them. A place whose law changed last year talks about it; a place whose law has not
     * moved in a century does not.
     *
     * <p>Where the pack has nothing, this says so rather than inventing a statute.
     */
    public static String toLaw(double lat, double lon, int year) {
        String qid = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(RulerRegister.Hit::wikidata)
                .orElse(null);
        LawRegister.Change last = LawRegister.get().latest(qid, year);
        if (last == null) {
            return "Nobody here speaks of the law changing; as far as the record goes, it has not.";
        }
        int ago = year - last.year();
        if (ago <= 2) {
            return "The law has just changed, and the place is full of it: " + last.line();
        }
        if (ago <= 25) {
            return "They still talk about " + last.year() + ": " + last.clause();
        }
        if (ago <= 90) {
            return "The old men remember " + last.year() + ", when " + last.clause();
        }
        return "The last change to the law here that anyone wrote down was in " + last.year()
                + ": " + last.clause();
    }

    // ------------------------------------------------------------------ D13 (3): things out of their time

    /** Whether this thing has reached this place by this year. */
    public static boolean outOfTime(String techId, double lat, double lon, int year) {
        if (techId == null || techId.isBlank()) {
            return false;
        }
        com.eternalengine.technology.TechnologyAtlas atlas =
                com.eternalengine.technology.TechnologyAtlas.get();
        com.eternalengine.technology.TechnologyAtlas.Tech t = atlas.tech(techId);
        if (t == null) {
            return false;              // not a thing the atlas knows; not a thing to react to
        }
        int arrived = atlas.arrivalAt(t, lat, lon);
        return arrived == com.eternalengine.technology.TechnologyAtlas.NEVER || arrived > year;
    }

    /**
     * D13: how people here react to <b>strange clothes and things from the future</b>.
     *
     * <p>The test is the technology atlas: it knows the year each thing reached each point of the
     * map, so a thing is out of its time here when that year is still to come. What people make of
     * it is then a matter of where they stand: a society that has never seen a machine reads a
     * machine as a sign, and an industrial town reads it as a novelty someone has paid for.
     */
    public static String toStrange(String techId, double lat, double lon, int year, String what) {
        if (!outOfTime(techId, lat, lon, year)) {
            return "Nobody gives it a second look.";
        }
        SocialOrder.Order order = SocialOrder.get().hereAt(lat, lon, year);
        String thing = what == null || what.isBlank() ? "the thing you are carrying" : what;
        return switch (order.id()) {
            case "kinship" -> "They have no word for " + thing + ". They ask whether it is alive, "
                    + "and whether it obeys you.";
            case "classic_antiquity" -> "They take " + thing + " for a marvel, or for a thing of "
                    + "the gods. Someone asks what it would cost.";
            case "varnas" -> "They will not touch " + thing + ", and they will not stop looking at "
                    + "it. A man of standing sends a servant to carry it.";
            case "mandarin" -> "They take it to the magistrate as a curiosity, and the magistrate "
                    + "writes it down. " + thing + " becomes a matter of record before it becomes "
                    + "a matter of use.";
            case "estates" -> "The priest makes a sign over " + thing + ". Half the village thinks "
                    + "it is witchcraft and the other half is already asking the price.";
            case "islamic" -> "They crowd round " + thing + " and argue about whether a believer "
                    + "may own one.";
            case "japanese" -> "They do not reach for " + thing + ". A samurai asks, very quietly, "
                    + "where you came by it.";
            case "modern_classes" -> "They have heard of such a thing and have never seen one. It "
                    + "draws a crowd of people who consider themselves modern.";
            case "socialist" -> "They assume it is state property and ask which ministry issued it "
                    + "to you.";
            case "settler_colonial" -> "A settler wants to buy " + thing + " and everyone else is "
                    + "told to move along.";
            default -> "They have never seen the like of " + thing + ", and they say so to each "
                    + "other while looking straight at you.";
        };
    }
}
