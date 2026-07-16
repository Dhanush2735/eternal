package com.eternalengine.incident;

/**
 * Initial incident catalog. More historical incident types can be added without
 * changing the deterministic seed contract.
 */
public enum IncidentType {
    GOOD_HARVEST(IncidentKind.POSITIVE),
    TRADE_BOOM(IncidentKind.POSITIVE),
    DISEASE_CONTAINED(IncidentKind.POSITIVE),
    POPULAR_REFORM(IncidentKind.POSITIVE),
    SUPPLY_BREAKTHROUGH(IncidentKind.POSITIVE),
    TECH_BREAKTHROUGH(IncidentKind.POSITIVE),
    SUCCESSFUL_DIPLOMACY(IncidentKind.POSITIVE),

    FAMINE(IncidentKind.NEGATIVE),
    PLAGUE(IncidentKind.NEGATIVE),
    REBELLION(IncidentKind.NEGATIVE),
    TRADE_ROUTE_RAID(IncidentKind.NEGATIVE),
    VEHICLE_ACCIDENT(IncidentKind.NEGATIVE),
    BORDER_SKIRMISH(IncidentKind.NEGATIVE),
    ECONOMIC_CRASH(IncidentKind.NEGATIVE),
    ASSASSINATION_ATTEMPT(IncidentKind.NEGATIVE),
    INFRASTRUCTURE_COLLAPSE(IncidentKind.NEGATIVE),

    LOCAL_RUMOR(IncidentKind.NEUTRAL),
    MINOR_MIGRATION(IncidentKind.NEUTRAL),
    MARKET_SHIFT(IncidentKind.NEUTRAL);

    private final IncidentKind kind;

    IncidentType(IncidentKind kind) {
        this.kind = kind;
    }

    public IncidentKind kind() {
        return kind;
    }
}
