package com.eternalengine.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, read-only checks of snapshots taken on the Minecraft server thread. */
public final class R2PeopleChecks {
    private R2PeopleChecks() {}
    public enum Status { OK, FAIL, KNOWN }
    public record Result(Status status, String text, String detail) {}
    public record Npc(String uuid, String kind, String civilization, String role, List<String> vipIds) {}
    public record Vip(String id, String name, int birth, int death, double lat, double lon,
                      int eventLinks, boolean dead, boolean claimed) {}

    public static List<Result> evaluate(List<Npc> nearby, boolean playerPresent, List<Vip> slots,
                                        boolean registryAvailable, Integer year) {
        List<Result> out = new ArrayList<>();
        Map<String, Vip> byId = new HashMap<>();
        List<String> badSlots = new ArrayList<>();
        int defaultLike = 0, links = 0, lifeEligible = 0;
        for (Vip v : slots) {
            if (v.id() == null || v.id().isBlank() || byId.put(v.id(), v) != null
                    || v.name() == null || v.name().isBlank() || v.birth() > v.death()
                    || !Double.isFinite(v.lat()) || !Double.isFinite(v.lon())
                    || Math.abs(v.lat()) > 90 || Math.abs(v.lon()) > 180) {
                badSlots.add(String.valueOf(v.id()));
            }
            if (v.birth() == -3000 || v.death() == 9999 || (v.lat() == 0 && v.lon() == 0)) defaultLike++;
            if (v.eventLinks() > 0) links++;
            if (year != null && !v.dead() && !v.claimed() && v.birth() <= year && year <= v.death()) lifeEligible++;
        }
        if (!registryAvailable || slots.isEmpty()) out.add(new Result(Status.FAIL,
                "VIP registry is unavailable or empty", "The engine needs an initialized, populated VIP registry."));
        else out.add(new Result(badSlots.isEmpty() ? Status.OK : Status.FAIL,
                "VIP registry: " + slots.size() + " slots; IDs, dates and coordinates checked",
                badSlots.isEmpty() ? "Field shape only; dates, roles and locations are not historically certified." : "Invalid or duplicate IDs/fields: " + String.join(", ", badSlots)));
        if (defaultLike > 0) out.add(new Result(Status.KNOWN,
                "VIP registry: " + defaultLike + " slots use values resembling missing-data defaults",
                "-3000, 9999 or 0,0 may be defaults; inspect provenance before spawning. A match alone is not proof of bad data."));
        if (registryAvailable && !slots.isEmpty()) {
            if (year != null) out.add(new Result(Status.KNOWN,
                    "VIPs: " + lifeEligible + " alive-by-date, unclaimed and not marked dead in year " + year,
                    "This is a date/state count, not a spawn or a precondition check."));
            out.add(new Result(Status.KNOWN, "VIP data source was not verified",
                    "The VIP loader can silently keep defaults when its installed file is missing or unreadable; a populated registry does not prove the file loaded."));
            out.add(new Result(Status.KNOWN, "VIP history links: " + links + " of " + slots.size() + " slots have event IDs",
                    "IDs alone do not prove event resolution, historical behavior or player-triggered alternate timelines. Those remain untested."));
        }
        if (!playerPresent) {
            out.add(new Result(Status.KNOWN, "Nearby NPC and VIP checks skipped: run as a player in the world",
                    "No player position is available from console; the registry check above still ran."));
            return List.copyOf(out);
        }
        Set<String> uuids = new HashSet<>();
        List<String> npcIssues = new ArrayList<>(), vipIssues = new ArrayList<>(), stateWarnings = new ArrayList<>();
        int ordinary = 0, tagged = 0;
        for (Npc n : nearby) {
            if (n.uuid() == null || n.uuid().isBlank() || !uuids.add(n.uuid())) npcIssues.add("missing/duplicate UUID " + n.uuid());
            if (n.vipIds().isEmpty()) {
                ordinary++;
                if ("human".equals(n.kind()) && (n.civilization() == null || n.civilization().isBlank()
                        || n.role() == null || n.role().isBlank())) npcIssues.add(n.uuid() + " missing role/civilization");
            } else {
                tagged++;
                if (n.vipIds().size() != 1) vipIssues.add(n.uuid() + " has " + n.vipIds().size() + " VIP tags");
                for (String id : n.vipIds()) {
                    Vip v = byId.get(id);
                    if (id.isBlank() || (registryAvailable && v == null)) vipIssues.add(n.uuid() + " unknown VIP ID " + id);
                    else if (v != null && (v.dead() || v.claimed() || (year != null && (year < v.birth() || year > v.death()))))
                        stateWarnings.add(id);
                }
            }
        }
        if (!npcIssues.isEmpty() && ordinary == 0) out.add(new Result(Status.FAIL,
                "Nearby entity identity check", String.join("; ", npcIssues)));
        if (ordinary == 0) out.add(new Result(Status.KNOWN, "Ordinary NPC check skipped: none nearby",
                "Stand near an ordinary Eternal human or Minecraft villager and run /ee check again."));
        else out.add(new Result(npcIssues.isEmpty() ? Status.OK : Status.FAIL,
                "Nearby ordinary NPCs: " + ordinary + "; unique IDs and Eternal human fields checked",
                npcIssues.isEmpty() ? "Right-click and observe interaction manually; this check does not interact or spawn." : String.join("; ", npcIssues)));
        if (tagged == 0) out.add(new Result(Status.KNOWN, "VIP entity check skipped: no VIP-tagged NPC nearby",
                "A registered VIP is not the same as a spawned VIP entity."));
        else if (!registryAvailable) out.add(new Result(Status.KNOWN, "Nearby VIP entities: " + tagged + "; registry unavailable",
                "Cannot verify their IDs without the VIP registry."));
        else out.add(new Result(vipIssues.isEmpty() ? Status.OK : Status.FAIL,
                "Nearby VIP entities: " + tagged + "; tags matched to the live registry",
                vipIssues.isEmpty() ? "Tag/registry check only; spawn behavior and historical actions were not tested." : String.join("; ", vipIssues)));
        if (!stateWarnings.isEmpty()) out.add(new Result(Status.KNOWN,
                "VIP entity state needs review: " + String.join(", ", stateWarnings),
                "A nearby tagged entity is outside recorded lifespan, marked dead or claimed; alternate timelines and world state can change expectations."));
        out.add(new Result(Status.KNOWN, "NPC interaction and VIP historical behavior were not exercised",
                "Talk to an NPC manually. The check does not trigger actions, spawn, kill, teleport, save or alter the timeline."));
        return List.copyOf(out);
    }
}
