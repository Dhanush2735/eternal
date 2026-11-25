package com.eternalengine.player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns real player actions in the world into a history fork — with no command typed.
 *
 * <h2>Why this class exists</h2>
 * Before this, every path that created a branch hung off a permission-level-2 command:
 * {@code /ee timeline diverge}, {@code /ee timeline cascade}, {@code /ee event prevent}. A player
 * who physically stopped the attackers at the Taj in 2008 changed nothing at all. The engine could
 * fork history; it just had no way to notice that a human had intervened.
 *
 * <p>{@link PlayerActionDetector} already observed every kill, block break, block place and right
 * click. Those observations were stored and then never evaluated. This class is the missing
 * evaluation.
 *
 * <h2>The rule</h2>
 * An intervention is <b>force applied at the event's own place, inside the event's own window of
 * years</b>. Nothing else counts. Specifically:
 *
 * <ul>
 *   <li>Actions are weighted by what they actually do. Killing hostiles at the site is stopping
 *       attackers, and counts fully. Placing or breaking blocks is building a barricade or cutting a
 *       route, and counts a quarter. Killing villagers or other players counts <b>nothing</b> — that
 *       is not an intervention, it is a second atrocity.</li>
 *   <li>Weight must cross a threshold, and come from more than one action. A single stray kill never
 *       rewrites a century.</li>
 *   <li>Evidence decays. Force applied forty years earlier is not force applied now.</li>
 *   <li><b>A disaster is never preventable.</b> Plagues, famines, earthquakes and epidemics are
 *       refused outright, however much a player does. Nobody stops a plague by standing in it. This
 *       is the same rule the canon encodes as {@code forkPotential: "none"}.</li>
 * </ul>
 *
 * <h2>Why it is pure Java</h2>
 * There is not one {@code net.minecraft} import here. The Minecraft-facing pieces — turning a
 * {@code BlockPos} into a latitude and longitude, and actually creating the branch — are injected as
 * {@link EventLocator} and {@link InterventionSink}. That is deliberate: it means the decision that
 * rewrites history can be tested without booting a server, which is how it was developed.
 *
 * <p>This class never decides <em>what happens next</em>. It only decides <em>whether a player has
 * intervened</em>, and hands that to the timeline machinery, which owns everything else.
 */
public final class InWorldInterventionDetector {

    /** An event as the detector sees it: a place, a window of years, and what kind of thing it is. */
    public record LocatedEvent(String id, String name, double lat, double lon,
                               int startYear, int endYear, double radiusKm, String kind) {
        public LocatedEvent {
            id = id == null ? "" : id;
            name = name == null ? id : name;
            kind = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        }

        public boolean activeIn(int year) {
            return year >= startYear && year <= endYear;
        }
    }

    /** Resolves a world position and year to the historical event happening there. */
    @FunctionalInterface
    public interface EventLocator {
        Optional<LocatedEvent> find(double lat, double lon, int year);
    }

    /** Creates the branch. Returns true only if history actually changed. */
    @FunctionalInterface
    public interface InterventionSink {
        boolean stop(LocatedEvent event, PlayerAction cause, String reason);
    }

    /**
     * Kinds a player can plausibly stop by acting.
     *
     * <p>Absence from this set is a refusal, not an omission. A battle has attackers; a plague does
     * not. Offering a player a way to defeat a plague would make the game lie about what history is.
     */
    public static final Set<String> STOPPABLE_KINDS =
            Set.of("battle", "siege", "incident", "political", "military", "war", "revolt", "raid");

    /** Kinds explicitly refused, listed by name so the refusal is visible in review. */
    public static final Set<String> REFUSED_KINDS =
            Set.of("disaster", "plague", "epidemic", "pandemic", "famine", "earthquake",
                   "flood", "tsunami", "drought", "fire", "storm", "climate", "volcano");

    /** Default force required to stop an event. Roughly: several real opposing actions. */
    private static final double DEFAULT_THRESHOLD = 6.0d;

    /** Below this many separate actions, no amount of weight is enough. */
    private static final int MIN_ACTIONS = 3;

    /** Evidence older than this many simulated years is stale and is discarded. */
    private static final int EVIDENCE_WINDOW_YEARS = 3;

    private final EventLocator locator;
    private final InterventionSink sink;
    private final double threshold;
    private final ConcurrentHashMap<String, Ledger> ledgers = new ConcurrentHashMap<>();
    private final Set<String> decided = ConcurrentHashMap.newKeySet();

    public InWorldInterventionDetector(EventLocator locator, InterventionSink sink) {
        this(locator, sink, DEFAULT_THRESHOLD);
    }

    public InWorldInterventionDetector(EventLocator locator, InterventionSink sink, double threshold) {
        this.locator = locator;
        this.sink = sink;
        this.threshold = threshold;
    }

    // ==================== the entry point ====================

    /**
     * Feed one observed player action. Cheap and safe to call from a Forge event handler.
     *
     * @return a line describing what happened, or empty if nothing did. Intended for the player.
     */
    public Optional<String> observe(PlayerAction action, double lat, double lon) {
        if (action == null) {
            return Optional.empty();
        }
        double weight = weightOf(action.type());
        if (weight <= 0.0d) {
            return Optional.empty();
        }

        Optional<LocatedEvent> found = locator.find(lat, lon, action.year());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LocatedEvent event = found.get();

        // Refusals come first, and are final. A plague is not a target.
        //
        // 2026-10-27: the kind is recovered through EventKind, because the worldwide corpus types all 1,141 of
        // its events as "event", which is in neither list - so before this, every event outside the Deccan set
        // was refused and a player could not fork history anywhere else in the world. EventKind only ever
        // returns a kind this class already trusts, and it makes disasters lose first.
        String kind = EventKind.effective(event.kind(), event.name());
        if (REFUSED_KINDS.contains(kind) || !STOPPABLE_KINDS.contains(kind)) {
            if (decided.add(refusalKey(event))) {
                return Optional.of("§8[History] §7Nothing you do here can stop " + event.name()
                        + ". It is not the kind of thing a person can stand in front of.");
            }
            return Optional.empty();
        }

        // Already dealt with on this branch.
        if (decided.contains(event.id())) {
            return Optional.empty();
        }

        // The window: force applied before the event begins is preparation; force applied after it
        // has ended is a story about the past. Only during is an intervention.
        if (!event.activeIn(action.year())) {
            return Optional.empty();
        }

        Ledger ledger = ledgers.computeIfAbsent(event.id(), id -> new Ledger());
        synchronized (ledger) {
            ledger.purge(action.year());
            ledger.add(weight, action, action.year());
            if (ledger.actions < MIN_ACTIONS || ledger.weight < threshold) {
                return Optional.of(progressLine(event, ledger));
            }
        }

        if (!decided.add(event.id())) {
            return Optional.empty();
        }

        String reason = buildReason(event, ledger, action);
        boolean stopped;
        try {
            stopped = sink.stop(event, action, reason);
        } catch (RuntimeException e) {
            // A failed fork must not be reported as a success, and must not be retried forever.
            decided.remove(event.id());
            return Optional.of("§c[History] §7The intervention at " + event.name()
                    + " could not be recorded: " + e.getMessage());
        }
        if (!stopped) {
            decided.remove(event.id());
            return Optional.empty();
        }
        return Optional.of("§6[History] §r" + event.name() + " §7did not happen. "
                + "§8The world has moved to another course, and nothing will tell you whether it is better.");
    }

    // ==================== weighting ====================

    /**
     * What each action is worth as an intervention.
     *
     * <p>The zeros matter more than the ones. Killing villagers and killing players are the player
     * participating in the violence, not stopping it, and must never accumulate toward a fork — or
     * the game would reward massacring a town by handing the player a new timeline.
     */
    public static double weightOf(PlayerActionType type) {
        if (type == null) {
            return 0.0d;
        }
        return switch (type) {
            case KILL_HOSTILE -> 1.0d;
            case KILL_VIP_CANDIDATE -> 1.0d;
            case PLACE_BLOCK -> 0.25d;
            case BREAK_BLOCK -> 0.25d;
            case INTERACT_BLOCK -> 0.10d;
            // Not interventions. Deliberately zero.
            case KILL_VILLAGER, KILL_PLAYER, PICKUP_ITEM, CLAIM_VIP, RELEASE_VIP,
                 COMMAND_ACTION, UNKNOWN -> 0.0d;
        };
    }

    private static String buildReason(LocatedEvent event, Ledger ledger, PlayerAction last) {
        return "In-world intervention at " + event.name()
                + ": " + ledger.actions + " action(s), force " + Math.round(ledger.weight)
                + " of " + Math.round(ledger.weight * 10) / 10.0d
                + ", by " + last.playerName() + " in " + last.year()
                + ". Last action: " + last.details();
    }

    private static String progressLine(LocatedEvent event, Ledger ledger) {
        return "§7[History] §8" + event.name() + " - " + ledger.actions
                + " action(s) here, force " + String.format(Locale.ROOT, "%.1f", ledger.weight) + ".";
    }

    private static String refusalKey(LocatedEvent event) {
        return "refused:" + event.id();
    }

    // ==================== bookkeeping ====================

    private static final class Ledger {
        double weight;
        int actions;
        int lastYear = Integer.MIN_VALUE;
        final java.util.ArrayDeque<int[]> history = new java.util.ArrayDeque<>(); // [weight×100, year]

        void add(double w, PlayerAction action, int year) {
            weight += w;
            actions++;
            lastYear = year;
            history.addLast(new int[] {(int) Math.round(w * 100), year});
        }

        /** Drop evidence older than the window. Force applied long ago is not force applied now. */
        void purge(int currentYear) {
            while (!history.isEmpty() && currentYear - history.peekFirst()[1] > EVIDENCE_WINDOW_YEARS) {
                int[] old = history.removeFirst();
                weight -= old[0] / 100.0d;
                actions = Math.max(0, actions - 1);
            }
        }
    }

    // ==================== inspection, for tests and for /ee ====================

    public double threshold() { return threshold; }

    public boolean hasDecided(String eventId) { return decided.contains(eventId); }

    public double forceAt(String eventId) {
        Ledger l = ledgers.get(eventId);
        if (l == null) {
            return 0.0d;
        }
        synchronized (l) {
            return l.weight;
        }
    }

    public int actionsAt(String eventId) {
        Ledger l = ledgers.get(eventId);
        if (l == null) {
            return 0;
        }
        synchronized (l) {
            return l.actions;
        }
    }

    /** Events this detector has refused, with the reason. Used to prove the refusal in testing. */
    public List<String> refusedEventIds() {
        return decided.stream().filter(s -> s.startsWith("refused:")).map(s -> s.substring(8))
                .sorted(Comparator.naturalOrder()).toList();
    }

    public Map<String, Double> forceByEvent() {
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        ledgers.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            synchronized (e.getValue()) {
                out.put(e.getKey(), e.getValue().weight);
            }
        });
        return out;
    }
}
