package com.eternalengine.affairs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable current-affairs briefing generated from server facts.
 */
public final class CurrentAffairsBriefing {

    private final int year;
    private final String regionId;
    private final AffairsScope scope;
    private final BotAffairsRole role;
    private final List<CurrentAffairsFact> facts;
    private final List<String> roleFocus;
    private final String warning;

    public CurrentAffairsBriefing(int year, String regionId, AffairsScope scope, BotAffairsRole role,
                                  List<CurrentAffairsFact> facts, List<String> roleFocus, String warning) {
        this.year = year;
        this.regionId = regionId == null || regionId.isBlank() ? "global" : regionId;
        this.scope = scope == null ? AffairsScope.LOCAL : scope;
        this.role = role == null ? BotAffairsRole.VILLAGER : role;
        List<CurrentAffairsFact> sorted = new ArrayList<>(facts == null ? List.of() : facts);
        sorted.sort(Comparator.comparingInt(CurrentAffairsFact::priority).reversed());
        this.facts = Collections.unmodifiableList(sorted);
        this.roleFocus = Collections.unmodifiableList(new ArrayList<>(roleFocus == null ? List.of() : roleFocus));
        this.warning = warning;
    }

    public String toChatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Current affairs [").append(scope).append("/").append(role).append("] year=").append(year)
            .append(" region=").append(regionId).append(": ");
        if (facts.isEmpty()) {
            sb.append("No major known affairs.");
        } else {
            int count = 0;
            for (CurrentAffairsFact fact : facts) {
                if (count++ > 0) {
                    sb.append(" ");
                }
                sb.append(fact.text());
                if (count >= 5) {
                    break;
                }
            }
        }
        if (warning != null && !warning.isBlank()) {
            sb.append(" Warning: ").append(warning);
        }
        return sb.toString();
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CurrentAffairsBriefing\n")
            .append("  year=").append(year).append(" region=").append(regionId)
            .append(" scope=").append(scope).append(" role=").append(role).append("\n")
            .append("  focus=").append(roleFocus).append("\n")
            .append("  facts:");
        if (facts.isEmpty()) {
            sb.append("\n    - No major known affairs.");
        } else {
            for (CurrentAffairsFact fact : facts) {
                sb.append("\n    - [").append(fact.category()).append("] ").append(fact.text());
            }
        }
        if (warning != null && !warning.isBlank()) {
            sb.append("\n  warning=").append(warning);
        }
        return sb.toString();
    }

    public int year() { return year; }
    public String regionId() { return regionId; }
    public AffairsScope scope() { return scope; }
    public BotAffairsRole role() { return role; }
    public List<CurrentAffairsFact> facts() { return facts; }
    public List<String> roleFocus() { return roleFocus; }
    public String warning() { return warning; }
}
