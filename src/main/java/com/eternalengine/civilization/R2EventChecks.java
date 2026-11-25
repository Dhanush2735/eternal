package com.eternalengine.civilization;

import com.eternalengine.knowledge.NewsIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only, sampled checks of the R2 event registers; never changes the player's timeline. */
public final class R2EventChecks {
    private R2EventChecks() {}
    public enum Status { OK, FAIL, KNOWN }
    public record Result(Status status, String text, String detail) {}
    private record Sample(String id, int start, int end, String kind, String effect) {}
    private static final List<Sample> SAMPLES = List.of(
            new Sample("q12950513", -51, -50, "siege", "military_presence"),
            new Sample("q619030", -49, -48, "battle", "military_presence"),
            new Sample("q3636319", -30, -29, "battle", "military_presence"),
            new Sample("wpy_7aa1a7bf55", -40, -39, "political", "political"),
            new Sample("wpy_fc57ada3d4", -20, -19, "political", "political"),
            new Sample("r2_solarium_augusti_10_bce", -10, -9, "cultural", "note"));
    private static final List<String> NOTES = List.of("hz_0ce0270de1", "hz_c82c5fc69e", "hz_91615f6219", "hz_771d3935b5");

    public static List<Result> run() {
        Disasters d = Disasters.get();
        Set<String> hazards = new HashSet<>();
        for (Disasters.Disaster x : d.all()) hazards.add(x.id());
        return evaluate(HistoryGazetteer.get().allEvents(), NewsIndex.all(), hazards, d.loaded());
    }

    /** Exposed to the headless regression probe; the same code runs inside /ee check. */
    public static List<Result> evaluate(List<HistoryGazetteer.Event> events, List<NewsIndex.Event> news,
                                        Set<String> hazards, boolean disastersLoaded) {
        List<Result> out = new ArrayList<>();
        Map<String, List<HistoryGazetteer.Event>> byId = new HashMap<>();
        for (HistoryGazetteer.Event e : events) byId.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(e);
        List<String> errors = new ArrayList<>();
        List<HistoryGazetteer.Event> sampled = new ArrayList<>();
        for (Sample s : SAMPLES) {
            List<HistoryGazetteer.Event> rows = byId.getOrDefault(s.id(), List.of());
            if (rows.size() != 1) { errors.add(s.id() + " count=" + rows.size()); continue; }
            var e = rows.get(0); sampled.add(e);
            if (e.start() != s.start() || e.end() != s.end() || !s.kind().equals(e.kind())
                    || !s.effect().equals(e.effect()) || !e.activeIn(s.start()) || e.activeIn(s.end())) {
                errors.add(s.id() + " has different year/kind/effect or end rule");
            }
        }
        out.add(new Result(errors.isEmpty() ? Status.OK : Status.FAIL,
                "R2 history sample: six reviewed events and Gazetteer year boundaries",
                errors.isEmpty() ? "Raw register only; suppression in an alternate world is not tested." : String.join("; ", errors)));
        errors.clear();
        for (String id : NOTES) {
            List<HistoryGazetteer.Event> rows = byId.getOrDefault(id, List.of());
            if (rows.size() != 1 || !"note".equals(rows.get(0).effect()) || hazards.contains(id)) errors.add(id);
        }
        if (!disastersLoaded) errors.add("disaster register did not load");
        out.add(new Result(errors.isEmpty() ? Status.OK : Status.FAIL,
                "R2 hazard sample: four uncertain reports are notes, not damaging hazards",
                errors.isEmpty() ? "Sample only; no disaster was generated." : "Missing, duplicated, still damaging or unreadable: " + String.join(", ", errors)));
        Map<String, List<NewsIndex.Event>> newsByName = new HashMap<>();
        for (NewsIndex.Event n : news) newsByName.computeIfAbsent(n.name(), k -> new ArrayList<>()).add(n);
        List<String> missing = new ArrayList<>(), differing = new ArrayList<>();
        for (var e : sampled) {
            List<NewsIndex.Event> same = newsByName.getOrDefault(e.name(), List.of());
            boolean present = same.stream().anyMatch(n -> n.start() == e.start());
            if (!present) { missing.add(e.id()); continue; }
            boolean match = same.stream().anyMatch(n -> n.start() == e.start() && n.end() == e.end() - 1);
            if (!match) differing.add(e.id());
        }
        if (sampled.size() != SAMPLES.size()) missing.add("some sample events unavailable");
        out.add(new Result(missing.isEmpty() ? Status.OK : Status.FAIL,
                "R2 news sample: the six events are indexed by name and start year",
                missing.isEmpty() ? "This does not test what an NPC actually says." : "Missing from NewsIndex: " + String.join(", ", missing)));
        if (!differing.isEmpty()) out.add(new Result(Status.KNOWN,
                "R2 news/Gazetteer end-year conventions differ for " + differing.size() + " sampled record(s)",
                String.join(", ", differing) + "; NewsIndex may include an extra year. Inspect before treating that year as correct."));
        out.add(new Result(Status.KNOWN, "History checks sample loaded data, not worldwide completeness or live outcomes",
                "Event scenes, actual disasters, player interventions, saves, and all other years are not proven here."));
        return List.copyOf(out);
    }
}
