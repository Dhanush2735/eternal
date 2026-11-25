package com.eternalengine.client;

import com.eternalengine.population.ChunkPopulation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of what the server last said the crowd is.
 *
 * <p>One recipe per place the player has been shown. Nothing here draws anything: this is the store
 * the drawing route reads from, so that the client can build the crowd's geometry from the recipe
 * rather than being sent the figures. A recipe is tiny — 166 bytes for a 64-block view — and it is
 * replaced whenever the server sends a new one, so the store never grows with the size of a city,
 * only with the number of places the player has stood in.
 */
public final class CrowdManifestStore {

    private CrowdManifestStore() {}

    /** place heart key -> the crowd there, as last sent by the server. */
    private static final Map<Long, ChunkPopulation.Manifest> CROWDS = new ConcurrentHashMap<>();

    /** A place's key: its heart, in blocks, and the year it is being shown in. */
    public static long key(int heartX, int heartZ, int year) {
        return ((long) heartX << 32) ^ (heartZ & 0xffffffffL) ^ ((long) year << 48);
    }

    /**
     * Takes a recipe from the server. A null recipe means the server has nothing to show here — the
     * player has left the place, or the year has turned — and the entry goes.
     */
    public static void accept(ChunkPopulation.Manifest manifest) {
        if (manifest == null) {
            return;
        }
        CROWDS.put(key(manifest.heartX(), manifest.heartZ(), manifest.year()), manifest);
    }

    /** Forgets a place: its crowd is no longer shown. */
    public static void forget(int heartX, int heartZ, int year) {
        CROWDS.remove(key(heartX, heartZ, year));
    }

    /** What to draw at this place, or null if the server has not said. */
    public static ChunkPopulation.Manifest crowdAt(int heartX, int heartZ, int year) {
        return CROWDS.get(key(heartX, heartZ, year));
    }

    /** Drops every crowd whose year is not this one — the clock turning, as the figures do. */
    public static int forgetYearsOtherThan(int year) {
        int removed = 0;
        for (Map.Entry<Long, ChunkPopulation.Manifest> entry : CROWDS.entrySet()) {
            if (entry.getValue().year() != year) {
                CROWDS.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    /** How many places the client is currently holding. */
    public static int size() {
        return CROWDS.size();
    }

    /** The whole store, for a renderer that wants to walk every place it knows. */
    public static Map<Long, ChunkPopulation.Manifest> all() {
        return CROWDS;
    }
}
