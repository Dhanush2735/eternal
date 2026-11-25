package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * VersionManager - Handles data version management and migrations.
 * 
 * Features:
 * - Data version constant
 * - Migration script registry
 * - Auto migrate on load
 * - Pre-migration backup always
 */
public class VersionManager {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static VersionManager instance;
    private static boolean initialized = false;
    
    // Current data version
    // 2 (step 14): state.json also holds the running world (wars, outbreaks, trade routes, world flags,
    // the clock's position inside the year, an operator's pause). Older saves simply lack those keys.
    public static final int CURRENT_VERSION = 2;
    
    // Migration registry
    private final List<Migration> migrations;
    
    private VersionManager() {
        this.migrations = new ArrayList<>();
        registerMigrations();
    }
    
    /**
     * Initialize the version manager
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[VersionManager] Already initialized");
            return;
        }
        
        instance = new VersionManager();
        initialized = true;
        
        LOGGER.info("[VersionManager] Initialized, current data version: {}", CURRENT_VERSION);
    }
    
    /** Whether {@link #initialize()} ran (callers that may run before it check this). */
    public static boolean isReady() {
        return initialized;
    }

    /**
     * Get the singleton instance
     */
    public static VersionManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("VersionManager not initialized!");
        }
        return instance;
    }
    
    /**
     * Register all migrations
     */
    private void registerMigrations() {
        // Migration from version 0 (no version) to version 1
        migrations.add(new Migration(0, 1, "Initial version migration") {
            @Override
            public JsonObject migrate(JsonObject data) {
                // Add version field if missing
                if (!data.has("dataVersion")) {
                    data.addProperty("dataVersion", 1);
                }
                return data;
            }
        });
        
        // Version 1 to 2: nothing to convert. The new keys are optional when read; an old save loads with
        // no wars, outbreaks, routes or flags, which is exactly what it had.
        migrations.add(new Migration(1, 2, "Running world state in state.json (step 14)") {
            @Override
            public JsonObject migrate(JsonObject data) {
                return data;
            }
        });

        // Register migrations here as data versions advance:
        // migrations.add(new Migration(1, 2, "Add new feature data") { ... });
    }
    
    /**
     * Check and migrate data if needed
     */
    public JsonObject migrateIfNeeded(JsonObject data, Path filePath) {
        int dataVersion = 0;
        if (data.has("dataVersion")) {
            dataVersion = data.get("dataVersion").getAsInt();
        }
        
        if (dataVersion >= CURRENT_VERSION) {
            return data; // No migration needed
        }
        
        LOGGER.info("[VersionManager] Migrating {} from version {} to {}", 
            filePath.getFileName(), dataVersion, CURRENT_VERSION);
        
        // Create backup before migration
        PersistenceManager.getInstance().createBackup(filePath);
        
        // Apply migrations in order
        JsonObject migratedData = data;
        for (Migration migration : migrations) {
            if (migration.fromVersion >= dataVersion && migration.toVersion <= CURRENT_VERSION) {
                LOGGER.info("[VersionManager] Applying migration: {}", migration.description);
                try {
                    migratedData = migration.migrate(migratedData);
                } catch (Exception e) {
                    LOGGER.error("[VersionManager] Migration failed: {}", e.getMessage());
                    throw new RuntimeException("Data migration failed", e);
                }
            }
        }
        
        // Update version
        migratedData.addProperty("dataVersion", CURRENT_VERSION);
        
        return migratedData;
    }
    
    /**
     * Check data version without migrating
     */
    public int getDataVersion(JsonObject data) {
        if (data.has("dataVersion")) {
            return data.get("dataVersion").getAsInt();
        }
        return 0;
    }
    
    /**
     * Check if migration is needed
     */
    public boolean needsMigration(JsonObject data) {
        return getDataVersion(data) < CURRENT_VERSION;
    }
    
    /**
     * Get current version
     */
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }
    
    /**
     * Get list of migrations
     */
    public List<String> getMigrationDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (Migration migration : migrations) {
            descriptions.add(String.format("v%d -> v%d: %s", 
                migration.fromVersion, migration.toVersion, migration.description));
        }
        return descriptions;
    }
    
    /**
     * Migration base class
     */
    private abstract static class Migration {
        final int fromVersion;
        final int toVersion;
        final String description;
        
        Migration(int fromVersion, int toVersion, String description) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.description = description;
        }
        
        abstract JsonObject migrate(JsonObject data);
    }
}
