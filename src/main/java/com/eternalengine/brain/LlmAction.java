package com.eternalengine.brain;

/**
 * Whitelisted action intents an external brain may propose.
 *
 * The brain never executes these directly. Eternal validates and routes them.
 */
public enum LlmAction {
    REQUEST_CURRENT_AFFAIRS,
    REQUEST_ANOMALY_EXPLANATION,
    DECLARE_WAR,
    OFFER_PEACE,
    MOVE_ARMY,
    OPEN_TRADE_ROUTE,
    CLOSE_TRADE_ROUTE,
    QUARANTINE_REGION,
    PROPOSE_BUILDING,
    PROPOSE_INCIDENT_RESPONSE,
    PROPOSE_VEHICLE_ROUTE,
    UNKNOWN;

    public boolean requiresVipAuthority() {
        return switch (this) {
            case DECLARE_WAR, OFFER_PEACE, MOVE_ARMY, OPEN_TRADE_ROUTE,
                 CLOSE_TRADE_ROUTE, QUARANTINE_REGION, PROPOSE_INCIDENT_RESPONSE,
                 PROPOSE_VEHICLE_ROUTE -> true;
            case REQUEST_CURRENT_AFFAIRS, REQUEST_ANOMALY_EXPLANATION, PROPOSE_BUILDING, UNKNOWN -> false;
        };
    }

    public boolean isReadOnly() {
        return this == REQUEST_CURRENT_AFFAIRS || this == REQUEST_ANOMALY_EXPLANATION;
    }
}
