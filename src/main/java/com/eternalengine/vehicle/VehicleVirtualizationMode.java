package com.eternalengine.vehicle;

/**
 * Vehicle representation mode.
 */
public enum VehicleVirtualizationMode {
    /** Simulated only in server state; no entity exists. */
    VIRTUAL,
    /** Real entity/blocks may be spawned near players. */
    MATERIALIZED,
    /** Route is paused because required chunks/state are unavailable. */
    SUSPENDED
}
