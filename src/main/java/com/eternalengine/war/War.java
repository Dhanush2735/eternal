package com.eternalengine.war;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
