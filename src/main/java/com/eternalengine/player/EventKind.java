package com.eternalengine.player;

import java.util.Locale;
import java.util.Set;

/**
 * <b>What kind of thing an event is, when the data only says "event".</b> 2026-10-27.
 *
 * <h2>The bug this fixes</h2>
 *
 * <p>{@link InWorldInterventionDetector} only lets a player stop an event whose kind is one of
 * {@code battle, siege, incident, political, military, war, revolt, raid}, and refuses every other kind. But the
 * worldwide dated-event corpus ({@code history/dated_events.json}, 1,141 events) is typed entirely as
 * <b>{@code "event"}</b> - a word that is in neither list. So the detector refused <b>every event in the
 * world</b>. The butterfly effect could only ever fire from the 248-event Deccan set, which is properly typed;
 * everywhere else a player could stand in the middle of a battle and the engine would say nothing.
 *
 * <p>That is why "the butterfly effect across Earth" did not exist: not a missing model, a missing word.
 *
 * <h2>How the kind is recovered, and why this is safe</h2>
 *
 * <p>The title carries the kind in plain language - "Battle of X", "Siege of Y", "Plague in Z". The classifier
 * reads the title and nothing else, because inventing a kind from a description would be guessing. Two rules
 * keep it conservative:
 *
 * <ul>
 *   <li><b>Disasters are matched first and always lose.</b> A plague is not a thing a player can stand in front
 *       of, and the whole point of the refusal list is that no amount of force stops one. If a title says
 *       "earthquake" it stays a disaster even when it also says "war".</li>
 *   <li><b>Anything unrecognised stays {@code "event"} and is still refused.</b> The classifier can only widen
 *       what a player may stop by naming a kind the detector already trusts; it can never invent a new power.</li>
 * </ul>
 */
public final class EventKind {

    private EventKind() {
    }

    /** Kinds that mean "a disaster": never stoppable, whatever the player does. */
    private static final Set<String> DISASTERS = Set.of(
            "earthquake", "plague", "epidemic", "pandemic", "famine", "flood", "tsunami", "drought", "fire",
            "storm", "volcano", "eruption", "disaster");

    /** Words in a title that betray a disaster, checked before anything else. */
    private static final String[] DISASTER_WORDS = {
            "earthquake", "plague", "epidemic", "pandemic", "famine", "flood", "tsunami", "drought",
            "volcano", "eruption", "landslide", "avalanche", "famine", "pestilence", "smallpox", "cholera",
            "influenza", "typhus", "wildfire", "cyclone", "hurricane", "typhoon", "blizzard", "freshet",
    };

    /** Title word -> the kind the detector already trusts. Ordered: first match wins. */
    private static final String[][] TITLE_KINDS = {
            {"siege", "siege"},
            {"battle", "battle"}, {"campaign", "battle"}, {"offensive", "battle"}, {"raid", "raid"},
            {"massacre", "battle"}, {"expedition", "military"},
            // Found by ButterflyReachProbe on 2026-10-27: 90 events stayed refused because their titles use
            // the plain verbs of warfare - "Capture of Santarem", "Sacking of Bergen", "Skirmish at the
            // Strait of Malacca". They are battles by any reasonable reading, so they are battles here.
            {"capture", "battle"}, {"recapture", "battle"}, {"sacking", "battle"}, {"sack of", "battle"},
            {"assault", "battle"}, {"attack on", "battle"}, {"skirmish", "battle"}, {"ambush", "battle"},
            {"storming", "battle"}, {"bombardment", "battle"}, {"blockade", "military"},
            {"invasion", "military"}, {"landing", "military"}, {"conquest", "military"},
            {"relief of", "military"}, {"defence of", "military"}, {"defense of", "military"},
            {"expulsion", "military"}, {"recovery of", "military"}, {"fall of", "battle"},
            {"destruction of", "battle"}, {"mutiny", "revolt"}, {"rising", "revolt"}, {"putsch", "revolt"},
            {"war", "war"}, {"revolt", "revolt"}, {"rebellion", "revolt"}, {"uprising", "revolt"},
            {"revolution", "revolt"}, {"coup", "political"}, {"assassination", "political"},
            {"treaty", "political"}, {"peace", "political"}, {"coronation", "political"},
            {"election", "political"}, {"independence", "political"}, {"partition", "political"},
            {"summit", "political"}, {"trial", "political"}, {"abdication", "political"},
            {"incident", "incident"},
            // The last 36 were mostly named the modern way: "Operation Spring Awakening", "First Zeitun
            // Resistance", "Surrender of Tranquebar". Same rule: only a kind the detector already trusts.
            {"operation", "battle"}, {"resistance", "revolt"}, {"surrender", "political"},
            {"evacuation", "military"}, {"withdrawal", "military"}, {"affair", "incident"},
    };

    /**
     * The kind the detector should judge this event by.
     *
     * @param rawKind the kind as stored in the file (often the generic {@code "event"})
     * @param name    the event's title - the only evidence used
     */
    public static String effective(String rawKind, String name) {
        String raw = rawKind == null ? "" : rawKind.trim().toLowerCase(Locale.ROOT);
        String title = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);

        // Rule 1: a disaster loses, always, even if it is also a battle. Checked on the stored kind first
        // (a correctly-typed disaster is left alone) and then on the title.
        if (DISASTERS.contains(raw)) {
            return raw;
        }
        for (String w : DISASTER_WORDS) {
            if (!title.contains(w)) {
                continue;
            }
            // "Storming of the Bastille" is a battle, not a storm. A naval "storm" is a real disaster, so the
            // word is only skipped when it is the verb.
            if ("storm".equals(w) && title.contains("storming")) {
                continue;
            }
            return w;
        }

        // A kind the detector already understands is passed through untouched.
        if (!raw.isEmpty() && !"event".equals(raw)) {
            return raw;
        }

        // Rule 2: recover the kind from the title, and only a kind the detector already trusts.
        for (String[] pair : TITLE_KINDS) {
            if (title.contains(pair[0])) {
                return pair[1];
            }
        }
        return raw.isEmpty() ? "event" : raw;
    }

    /** Whether the title reads as a disaster. Used by the probe and the check. */
    public static boolean looksLikeDisaster(String name) {
        String title = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String w : DISASTER_WORDS) {
            if (title.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
