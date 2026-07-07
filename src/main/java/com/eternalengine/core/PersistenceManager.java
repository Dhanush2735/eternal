package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * PersistenceManager - All file I/O goes through this class.
 *
 * Features:
 * - Gson JSON serialization
 * - FileLock for concurrent safety
 * - Atomic writes via temp file + rename
 * - Backup rotation keeping N copies
 * - Corruption detection on load
 * - Auto recover from last good backup
 * - All operations async off server thread (via AsyncTaskManager)
 */
public class PersistenceManager {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static PersistenceManager instance;
    private static boolean initialized = false;

    // Gson instance
    private final Gson gson;

    // File locks
    private final ConcurrentHashMap<String, FileLock> activeLocks;

    // Backup timestamp format
    private static final DateTimeFormatter BACKUP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private PersistenceManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .serializeNulls()
            .create();
        this.activeLocks = new ConcurrentHashMap<>();
    }

    /**
     * Initialize the persistence manager
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[PersistenceManager] Already initialized");
            return;
        }

        instance = new PersistenceManager();
        initialized = true;

        LOGGER.info("[PersistenceManager] Initialized");
    }

    /**
     * Get the singleton instance
     */
    public static PersistenceManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("PersistenceManager not initialized!");
        }
        return instance;
    }

    /**
     * Save JSON to file atomically (write to temp, then rename)
     * This is thread-safe and prevents corruption.
     */
    public boolean saveJsonAtomic(Path filePath, JsonObject data) {
        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        String pathKey = filePath.toString();

        try {
            // Ensure parent directory exists
            Files.createDirectories(filePath.getParent());

            // Write to temp file
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                 FileChannel channel = fos.getChannel();
                 FileLock lock = channel.lock()) {

                // Store lock reference
                activeLocks.put(pathKey, lock);

                // Write data
                String json = gson.toJson(data);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                channel.force(true); // Force write to disk
            }

            // Atomic rename
            Files.move(tempFile, filePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

            return true;

        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to save {}: {}", filePath, e.getMessage());

            // Clean up temp file if it exists
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}

            return false;

        } finally {
            activeLocks.remove(pathKey);
        }
    }

    /**
     * Save JSON asynchronously
     */
    public void saveJsonAsync(Path filePath, JsonObject data, Runnable onComplete) {
        AsyncTaskManager.getInstance().submit(
            "save_" + filePath.getFileName(),
            () -> saveJsonAtomic(filePath, data),
            success -> {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        );
    }

    /**
     * Load JSON from file with corruption detection
     */
    @Nullable
    public JsonObject loadJson(Path filePath) {
        if (!Files.exists(filePath)) {
            LOGGER.debug("[PersistenceManager] File not found: {}", filePath);
            return null;
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            // Validate JSON
            if (content == null || content.trim().isEmpty()) {
                LOGGER.warn("[PersistenceManager] Empty file: {}", filePath);
                return tryLoadBackup(filePath);
            }

            JsonObject json = gson.fromJson(content, JsonObject.class);

            if (json == null) {
                LOGGER.warn("[PersistenceManager] Invalid JSON in: {}", filePath);
                return tryLoadBackup(filePath);
            }

            return json;

        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to load {}: {}", filePath, e.getMessage());
            return tryLoadBackup(filePath);
        }
    }

    /**
     * Load JSON asynchronously
     */
    public void loadJsonAsync(Path filePath, java.util.function.Consumer<JsonObject> callback) {
        AsyncTaskManager.getInstance().submit(
            "load_" + filePath.getFileName(),
            () -> {
                JsonObject result = loadJson(filePath);
                AsyncTaskManager.getInstance().submit(
                    "callback_" + filePath.getFileName(),
                    () -> callback.accept(result),
                    null
                );
            }
        );
    }

    /**
     * Create a backup of a file
     */
    public boolean createBackup(Path filePath) {
        if (!Files.exists(filePath)) {
            return false;
        }

        try {
            Path backupDir = filePath.getParent().resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            String backupName = filePath.getFileName().toString().replace(".json", "")
                + "_" + timestamp + ".json";

            Path backupPath = backupDir.resolve(backupName);
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            LOGGER.info("[PersistenceManager] Created backup: {}", backupPath);

            // Rotate old backups
            rotateBackups(backupDir, filePath.getFileName().toString().replace(".json", ""));

            return true;

        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to create backup: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Rotate backups, keeping only the configured number
     * Fix: Explicitly typed Comparator<Path> so Java can infer FileTime correctly
     */
    private void rotateBackups(Path backupDir, String filePrefix) {
        int maxBackups = EternalEngineConfig.COMMON.maxBackups.get();

        try (Stream<Path> files = Files.list(backupDir)) {
            Comparator<Path> byTime = Comparator.comparing(
                (Path p) -> getFileTime(p)
            );

            List<Path> backups = files
                .filter(p -> p.getFileName().toString().startsWith(filePrefix))
                .sorted(byTime.reversed())
                .toList();

            // Delete old backups
            for (int i = maxBackups; i < backups.size(); i++) {
                Files.delete(backups.get(i));
                LOGGER.debug("[PersistenceManager] Deleted old backup: {}", backups.get(i));
            }

        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to rotate backups: {}", e.getMessage());
        }
    }

    /**
     * Try to load from backup if main file is corrupted
     * Fix: Explicitly typed Comparator<Path> so Java can infer FileTime correctly
     */
    @Nullable
    private JsonObject tryLoadBackup(Path filePath) {
        Path backupDir = filePath.getParent().resolve("backups");
        String filePrefix = filePath.getFileName().toString().replace(".json", "");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[PersistenceManager] No backup directory found");
            return null;
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            Comparator<Path> byTime = Comparator.comparing(
                (Path p) -> getFileTime(p)
            );

            Optional<Path> latestBackup = files
                .filter(p -> p.getFileName().toString().startsWith(filePrefix))
                .max(byTime);

            if (latestBackup.isPresent()) {
                LOGGER.info("[PersistenceManager] Recovering from backup: {}", latestBackup.get());

                String content = Files.readString(latestBackup.get(), StandardCharsets.UTF_8);
                JsonObject json = gson.fromJson(content, JsonObject.class);

                if (json != null) {
                    Files.copy(latestBackup.get(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[PersistenceManager] Restored from backup successfully");
                    return json;
                }
            }

        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to load backup: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Helper - safely get FileTime for a Path
     * Returns epoch 0 on failure so broken files sort to the bottom
     */
    private FileTime getFileTime(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /**
     * Delete a file safely
     */
    public boolean deleteFile(Path filePath) {
        try {
            return Files.deleteIfExists(filePath);
        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to delete {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a file exists
     */
    public boolean fileExists(Path filePath) {
        return Files.exists(filePath);
    }

    /**
     * Create directories if they don't exist
     */
    public boolean ensureDirectories(Path dirPath) {
        try {
            Files.createDirectories(dirPath);
            return true;
        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to create directories {}: {}",
                dirPath, e.getMessage());
            return false;
        }
    }

    /**
     * List files in a directory
     */
    public List<Path> listFiles(Path dirPath, String extension) {
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(dirPath)) {
            return files
                .filter(p -> p.getFileName().toString().endsWith(extension))
                .toList();
        } catch (Exception e) {
            LOGGER.error("[PersistenceManager] Failed to list files: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get the Gson instance for external use
     */
    public Gson getGson() {
        return gson;
    }
}