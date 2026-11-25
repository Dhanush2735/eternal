package com.eternalengine.story;

import com.eternalengine.clock.Timeline;
import com.eternalengine.timeline.Provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>The history and people of one ruptured universe.</b> H6, first half.
 *
 * <h2>Why this class exists</h2>
 *
 * H6 says the story goes on in the new universe: <b>its own history, people, papers and book</b>, with
 * <b>A1 with the universe; G3 rules</b>. A1 ({@code core/WorldKey}) already carries the universe field and
 * its own note says other universes "have no recorded history yet". This class is that history's first
 * half: the lines and the people, each keyed to one universe and one place.
 *
 * <h2>The guardrail, enforced here and not merely documented</h2>
 *
 * <p>The record ends at 2026 ({@link Timeline#RECORDED_UNTIL}). Everything past it is {@link
 * Timeline#PROJECTED}, and a projection may continue a state ("the sea is still high") but may <b>never
 * name a new ruler, person, war, event, disaster, price or population</b>. The mark is computed from the
 * year, never declared by the caller; and a projected line that names one of those subjects is
 * <b>refused</b>, naming the word that tripped it. A person is never projected at all: people come from
 * the record or from a named player (AUTHORED), never from an estimate.
 *
 * <h2>Every line says who wrote it</h2>
 *
 * <p>Each line and person carries a {@link Provenance} (step 51's discipline: a line that does not say
 * which one it is has no value) and an AUTHORED one must name its author, because a claim nobody signed is
 * a claim nobody can check.
 */
public final class UniverseStory {

    /** The subjects a projection may never name, and the words this class refuses on. */
    public static final List<String> GUARD_WORDS = List.of(
            "king", "queen", "emperor", "empress", "ruler", "president", "minister", "dynasty", "shah",
            "sultan", "pharaoh", "war", "battle", "siege", "invasion", "revolution", "army",
            "earthquake", "famine", "plague", "epidemic", "tsunami", "eruption",
            "price", "population", "census");

    /** <b>One line of the universe's history.</b> The mark is computed from the year, never declared. */
    public record Line(int year, String text, Provenance provenance, String mark, String source,
                       String place, String universe) {

        public Line {
            text = text == null ? "" : text.trim();
            source = source == null ? "" : source.trim();
            place = place == null ? "" : place.trim();
            universe = universe == null ? "" : universe.trim();
            mark = Timeline.markFor(year);
        }

        public String line() {
            return universe + " · " + place + " · " + year + " [" + mark + ", " + provenance.label()
                    + "] " + text;
        }
    }

    /** <b>One person of the universe.</b> Born and died are the record's years, or a player's. */
    public record Person(String name, Integer born, Integer died, String role, Provenance provenance,
                         String source, String universe) {

        public Person {
            name = name == null ? "" : name.trim();
            role = role == null ? "" : role.trim();
            source = source == null ? "" : source.trim();
            universe = universe == null ? "" : universe.trim();
        }

        public String line() {
            String years = (born == null ? "?" : born) + "-" + (died == null ? "?" : died);
            return name + " (" + years + (role.isEmpty() ? "" : ", " + role) + ") · " + provenance
                    + (source.isEmpty() ? "" : " · " + source) + " · " + universe;
        }
    }

    /** <b>The outcome of writing a line.</b> */
    public record LineWritten(Line line, List<String> refused) {
        public boolean ok() {
            return line != null;
        }
    }

    /** <b>The outcome of naming a person.</b> */
    public record PersonNamed(Person person, List<String> refused) {
        public boolean ok() {
            return person != null;
        }
    }

    private final String universe;
    private final List<Line> lines = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();

    private UniverseStory(String universe) {
        this.universe = universe;
    }

    /** The story of one universe. A universe with no name has no story, and this says so. */
    public static UniverseStory of(String universe) {
        return new UniverseStory(universe == null ? "" : universe.trim());
    }

    public String universe() {
        return universe;
    }

    /**
     * <b>Write a line of history.</b> Refused: a blank universe or place, a blank line, an AUTHORED line
     * with no author, and - past the record - any line naming a subject the guardrail forbids.
     */
    public LineWritten add(int year, String place, String text, Provenance provenance, String source) {
        List<String> refused = new ArrayList<>();
        if (universe.isEmpty()) {
            refused.add("a line that says which universe it belongs to: name the universe first");
        }
        if (place == null || place.trim().isEmpty()) {
            refused.add("a line that says where: name the place");
        }
        if (text == null || text.trim().isEmpty()) {
            refused.add("a blank line is not history");
        }
        if (provenance == null) {
            refused.add("a line that does not say who wrote it has no value");
        } else if (provenance == Provenance.AUTHORED
                && (source == null || source.trim().isEmpty())) {
            refused.add("an authored line signs its name: say who wrote it");
        }
        String guard = guardRefusal(year, text);
        if (guard != null) {
            refused.add(guard);
        }
        if (!refused.isEmpty()) {
            return new LineWritten(null, refused);
        }
        Line line = new Line(year, text, provenance, Timeline.markFor(year), source,
                place.trim(), universe);
        lines.add(line);
        return new LineWritten(line, List.of());
    }

    /**
     * <b>Name a person.</b> Refused: no name, a death before a birth, an authored person with no author,
     * and a person projected past the record (a person is never projected: the record's people or a
     * named player's people, nothing else).
     */
    public PersonNamed name(String personName, Integer born, Integer died, String role,
                            Provenance provenance, String source) {
        List<String> refused = new ArrayList<>();
        if (universe.isEmpty()) {
            refused.add("a person that says which universe they belong to: name the universe first");
        }
        if (personName == null || personName.trim().isEmpty()) {
            refused.add("a person with no name is nobody");
        }
        if (born != null && died != null && died < born) {
            refused.add(personName + " cannot die in " + died + ", which is before " + born);
        }
        if (provenance == null) {
            refused.add("a person that does not say who vouches for them has no value");
        } else if (provenance == Provenance.AUTHORED
                && (source == null || source.trim().isEmpty())) {
            refused.add("an authored person signs its name: say who wrote them");
        }
        if (born != null && Timeline.projected(born) && provenance != Provenance.AUTHORED) {
            refused.add("a person is never projected: " + personName + " born in " + born + " is past the "
                    + "record (" + Timeline.RECORDED_UNTIL + "), and only a named player may author "
                    + "someone the record does not hold");
        }
        if (!refused.isEmpty()) {
            return new PersonNamed(null, refused);
        }
        Person person = new Person(personName.trim(), born, died, role, provenance, source, universe);
        people.add(person);
        return new PersonNamed(person, List.of());
    }

    public List<Line> history() {
        return List.copyOf(lines);
    }

    public List<Person> people() {
        return List.copyOf(people);
    }

    /** The history page: every line, in year order, with its mark and its provenance. */
    public List<String> page() {
        List<String> out = new ArrayList<>();
        for (Line l : lines) {
            out.add(l.line());
        }
        return out; // insertion order: the universe's own order, not the year's (H4 bends it in part 4)
    }

    /** The people page. */
    public List<String> peoplePage() {
        List<String> out = new ArrayList<>();
        for (Person p : people) {
            out.add(p.line());
        }
        return out;
    }

    /** The guardrail note for a year, or null inside the record. */
    public static String guardFor(int year) {
        return Timeline.projectionNote(year);
    }

    /**
     * <b>The guardrail, as one reusable check.</b> Returns the refusal a projected text earns, or null
     * when it is lawful. Anything that shows words to a player (a history line, a paper story) asks this
     * first, so the guardrail is enforced in exactly one place.
     */
    public static String guardRefusal(int year, String text) {
        if (!Timeline.projected(year)) {
            return null;
        }
        String body = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String word : GUARD_WORDS) {
            if (body.contains(word)) {
                return "a projection past " + Timeline.RECORDED_UNTIL + " may continue a state but never "
                        + "name a new ruler, person, war, event, disaster, price or population: this "
                        + "line says \"" + word + "\"";
            }
        }
        return null;
    }

    public static String ruleLine() {
        return "A universe's history says where and when and who wrote it; the mark (recorded or "
                + "projected) is computed from the year and never declared. Past " + Timeline.RECORDED_UNTIL
                + " a line may continue a state but never names a new ruler, person, war, event, "
                + "disaster, price or population, and a person is never projected at all.";
    }
}
