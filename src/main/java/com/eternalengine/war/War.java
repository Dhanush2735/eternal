package com.eternalengine.war;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable war state.
 */
public final class War {
    private final String warId;
    private final String name;
    private final List<String> alliedFactionIds;
    private final List<String> opposedFactionIds;
    private final int startYear;
    private int endYear;
    private final List<double[]> frontLines;
    private long casualtiesPerMonth;
    private double warExhaustion;
    private WarStatus status;
    private final String triggerEventId;
    private final List<BattleRecord> battles;
    /**
     * What kind of party each faction in this war is, where a caller has said.
     *
     * <p>A faction id is not a party id: {@code civ_florence} is not {@code city_commune}, and
     * nothing can infer the second from the first. Step 40 part 5's rule is that the map cannot
     * tell you a town is a commune — only the town can — so the caller names it or the side
     * defaults to a sovereign.
     */
    private final Map<String, String> partyIds;
    /** Casualties counted before the last restart (the battle list itself is not saved). */
    private long priorCasualties;

    public War(String warId, String name, List<String> alliedFactionIds, List<String> opposedFactionIds,
               int startYear, String triggerEventId) {
        this.warId = warId == null || warId.isBlank() ? "war_unknown" : warId;
        this.name = name == null || name.isBlank() ? this.warId : name;
        this.alliedFactionIds = new ArrayList<>(alliedFactionIds == null ? List.of() : alliedFactionIds);
        this.opposedFactionIds = new ArrayList<>(opposedFactionIds == null ? List.of() : opposedFactionIds);
        this.startYear = startYear;
        this.endYear = -1;
        this.frontLines = new ArrayList<>();
        this.casualtiesPerMonth = 0L;
        this.warExhaustion = 0.0d;
        this.status = WarStatus.ACTIVE;
        this.triggerEventId = triggerEventId;
        this.battles = new ArrayList<>();
        this.partyIds = new LinkedHashMap<>();
    }

    /** Name the kind of party a faction is: a row id from the sides register. */
    public void setPartyOf(String factionId, String partyId) {
        if (factionId != null && !factionId.isBlank() && partyId != null && !partyId.isBlank()) {
            partyIds.put(factionId, partyId);
        }
    }

    /** The kind of party named for a faction, or null if nobody has said. */
    public String partyOf(String factionId) {
        return factionId == null ? null : partyIds.get(factionId);
    }

    /**
     * One side of this war, as the sides register sees it.
     *
     * <p>Factions with no party named are left out; {@link #alliedSide()} and
     * {@link #opposedSide()} fill the gap with the default sovereign, because a war between two
     * states is the ordinary case and a register that refused to answer until every faction were
     * named would be answering nothing.
     */
    public Sides.Side sideOf(List<String> factionIds) {
        List<String> ids = new ArrayList<>();
        for (String f : factionIds) {
            String p = partyOf(f);
            if (p != null) {
                ids.add(p);
            }
        }
        if (ids.isEmpty()) {
            ids.add("sovereign_state");
        }
        return Sides.side(ids);
    }

    public Sides.Side alliedSide() {
        return sideOf(alliedFactionIds);
    }

    public Sides.Side opposedSide() {
        return sideOf(opposedFactionIds);
    }

    /**
     * Can this war be ended by an agreement?
     *
     * <p>Every party on both sides has to be able to be held to it. One warlord band, one
     * unpaid hired company or one rising anywhere in a coalition is enough to make the whole war
     * unbargainable, which is the single most consequential thing in the sides register: it is
     * why the Italian city-states were sacked so often, and why a colonial war against a
     * confederacy with no one who can bind the rest does not end in a treaty.
     */
    public boolean canTreat() {
        return alliedSide().willTreat() && opposedSide().willTreat();
    }

    /** The party that would sign for each side, or null if neither can. */
    public String treatyLine() {
        Sides.Party a = alliedSide().speaker();
        Sides.Party b = opposedSide().speaker();
        return canTreat()
            ? (a == null ? "somebody" : a.name()) + " and " + (b == null ? "somebody" : b.name())
                + " can both be held to a treaty"
            : "no treaty is possible: " + (alliedSide().willTreat()
                ? opposedSide().speaker() == null ? "the opposing side" : opposedSide().speaker().name()
                : alliedSide().speaker() == null ? "the allied side" : alliedSide().speaker().name())
                + " cannot be bound by one";
    }

    public void addBattle(BattleRecord battle) {
        if (battle != null) {
            battles.add(battle);
        }
    }

    public void addFrontLinePoint(double lat, double lon) {
        frontLines.add(new double[] { lat, lon });
    }

    public void increaseExhaustion(double amount) {
        warExhaustion = Math.max(0.0d, Math.min(100.0d, warExhaustion + amount));
        if (warExhaustion >= 90.0d && status == WarStatus.ACTIVE) {
            status = WarStatus.STALEMATE;
        }
    }

    public void end(int year) {
        this.endYear = year;
        this.status = WarStatus.ENDED;
    }

    public String warId() { return warId; }
    public String name() { return name; }
    public List<String> alliedFactionIds() { return Collections.unmodifiableList(alliedFactionIds); }
    public List<String> opposedFactionIds() { return Collections.unmodifiableList(opposedFactionIds); }
    public int startYear() { return startYear; }
    public int endYear() { return endYear; }
    public List<double[]> frontLines() { return Collections.unmodifiableList(frontLines); }
    public long casualtiesPerMonth() { return casualtiesPerMonth; }
    public void setCasualtiesPerMonth(long casualtiesPerMonth) { this.casualtiesPerMonth = Math.max(0, casualtiesPerMonth); }
    public double warExhaustion() { return warExhaustion; }
    public WarStatus status() { return status; }
    public String triggerEventId() { return triggerEventId; }
    public List<BattleRecord> battles() { return Collections.unmodifiableList(battles); }
    public long priorCasualties() { return priorCasualties; }
    public void setPriorCasualties(long casualties) { this.priorCasualties = Math.max(0L, casualties); }
}
