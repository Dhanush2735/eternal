package com.eternalengine.data;

import com.eternalengine.EternalEngineMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Installs bundled starter JSON data into the world folder, and keeps it up to date (step 14).
 *
 * <h3>What a world gets</h3>
 * The files of {@link #fileMap}: the event chain, VIPs, buildings, civilizations, names, population packs,
 * stories (scenarios), plus religions, technologies, climate and templates (shipped for systems that do not
 * read them yet). The world history (rulers, events, people, places) is not copied: the mod reads it from
 * its own jar, so it is always the version of the installed mod. Until step 14 eleven history files were
 * copied as well and never read; worlds that have them keep them, unused.
 *
 * <h3>Mod updates</h3>
 * {@code <world>/eternalengine/installed_data.json} records the checksum of every file as the mod installed
 * it. At every world start ({@link #refreshAtStart}) each shipped file is compared with the world's copy:
 * <ul>
 *   <li>missing in the world: copied (a file new in this mod version);</li>
 *   <li>the same as the mod's: nothing to do;</li>
 *   <li>unchanged since the mod installed it, and the mod now ships a different one: replaced (a fix);</li>
 *   <li>changed by the server owner: kept, never overwritten;</li>
 *   <li>different and no record (installed before step 14): kept, because it may be the owner's edit;
 *       {@code /ee data install-overwrite} replaces it.</li>
 * </ul>
 * The data version is a short checksum over all shipped files, so it changes whenever any of them does.
 */
public final class FoundationDataInstaller {
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    private static final String BASE = "/data/eternalengine/eternal/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The record of what this world got from the mod, in the world's {@code eternalengine} folder. */
    public static final String MANIFEST = "installed_data.json";

    private FoundationDataInstaller() {
    }

    /**
     * Whether this world has an installed foundation copy.
     *
     * <p>Two sentinel files, one from each end of the install: the event pack and the civilization
     * table. Callers use this to decide whether refreshing from the world folder is meaningful, or
     * whether it would only replace present data with nothing.
     */
    public static boolean hasInstalledData(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        return Files.isRegularFile(EternalDataPaths.events(server).resolve("foundation_events.json"))
                && Files.isRegularFile(EternalDataPaths.civilizations(server)
                        .resolve("foundation_civilizations.json"));
    }

    /** What {@link #refreshAtStart} did when the current world started (null before any world started). */
    private static volatile AutoInstall lastAutomatic;
    private static volatile RefreshResult lastRefresh;
    private static volatile String lastRefreshWorld;

    /**
     * At world start, before step 14: a world nobody ran {@code /ee data install} in got the shipped copy;
     * a world that had its copy was left as it was. Kept for callers of the old name; it now does what
     * {@link #refreshAtStart} does.
     *
     * @return the install result when the world had no foundation data, otherwise empty
     */
    public static Optional<InstallResult> installIfMissing(MinecraftServer server) {
        RefreshResult r = refreshAtStart(server);
        if (r == null || !r.firstInstall()) {
            return Optional.empty();
        }
        return Optional.of(r.asInstallResult());
    }

    /**
     * World start (step 14): install the data into a world that has none, and bring a world's copy up to
     * date with this mod version without overwriting anything the owner changed. See the class comment.
     *
     * @return what happened to every file; null without a server
     */
    public static synchronized RefreshResult refreshAtStart(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        String world = worldKey(server);
        boolean first = !hasInstalledData(server);
        Map<String, String> record = readManifest(server);
        Map<String, Path> files = fileMap(server);
        Map<String, String> shipped = new TreeMap<>();
        List<FileStatus> statuses = new ArrayList<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String resource = entry.getKey();
            Path target = entry.getValue();
            byte[] jar = readResource(resource);
            if (jar == null) {
                statuses.add(new FileStatus(resource, FileState.FAILED, "missing from the mod jar"));
                continue;
            }
            String jarSha = sha256(jar);
            shipped.put(resource, jarSha);
            try {
                if (!Files.isRegularFile(target)) {
                    write(target, jar);
                    record.put(resource, jarSha);
                    statuses.add(new FileStatus(resource, FileState.ADDED, first ? "installed" : "new in this mod version"));
                    continue;
                }
                String worldSha = sha256(Files.readAllBytes(target));
                String installed = record.get(resource);
                if (worldSha.equals(jarSha)) {
                    record.put(resource, jarSha);
                    statuses.add(new FileStatus(resource, FileState.CURRENT, ""));
                } else if (installed != null && worldSha.equals(installed)) {
                    write(target, jar);
                    record.put(resource, jarSha);
                    statuses.add(new FileStatus(resource, FileState.UPDATED, "replaced by this mod version's copy"));
                } else if (installed != null) {
                    statuses.add(new FileStatus(resource, FileState.KEPT_EDITED, "changed in this world; kept"));
                } else {
                    statuses.add(new FileStatus(resource, FileState.KEPT_UNKNOWN,
                        "differs from this mod version's copy and was installed before the record existed; kept"));
                }
            } catch (Exception e) {
                statuses.add(new FileStatus(resource, FileState.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage()));
                LOGGER.warn("[FoundationDataInstaller] {}: {}", target, e.getMessage());
            }
        }
        String version = dataVersion(shipped);
        writeManifest(server, version, record);
        RefreshResult result = new RefreshResult(version, List.copyOf(statuses), first);
        lastRefresh = result;
        lastRefreshWorld = world;
        lastAutomatic = new AutoInstall(world, first, first ? result.asInstallResult() : null);
        if (first) {
            LOGGER.info("[FoundationDataInstaller] This world had no foundation data; installed data version {}: {}",
                version, result.summary());
        } else if (result.count(FileState.CURRENT) != statuses.size()) {
            LOGGER.info("[FoundationDataInstaller] World data checked against data version {}: {}", version,
                result.summary());
        }
        return result;
    }

    /** What happened at this world's start, for {@code /ee check all}; empty for another world or none. */
    public static Optional<AutoInstall> lastAutomatic(MinecraftServer server) {
        AutoInstall a = lastAutomatic;
        if (a == null || server == null) {
            return Optional.empty();
        }
        return worldKey(server).equals(a.world()) ? Optional.of(a) : Optional.empty();
    }

    /** The data check of this world's start, for {@code /ee data status} and {@code /ee check all}. */
    public static Optional<RefreshResult> lastRefresh(MinecraftServer server) {
        RefreshResult r = lastRefresh;
        if (r == null || server == null) {
            return Optional.empty();
        }
        return worldKey(server).equals(lastRefreshWorld) ? Optional.of(r) : Optional.empty();
    }

    private static String worldKey(MinecraftServer server) {
        return EternalDataPaths.root(server).toAbsolutePath().normalize().toString();
    }

    /** {@code ran}: the world had no foundation data at start and the shipped copy was installed then. */
    public record AutoInstall(String world, boolean ran, InstallResult result) {
    }

    public static synchronized InstallResult install(MinecraftServer server, boolean overwrite) {
        if (server == null) {
            return new InstallResult(0, 0, 1, "server is null");
        }
        Map<String, Path> files = fileMap(server);
        Map<String, String> record = readManifest(server);
        Map<String, String> shipped = new TreeMap<>();
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        StringBuilder messages = new StringBuilder();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String resource = entry.getKey();
            Path target = entry.getValue();
            byte[] jar = readResource(resource);
            if (jar == null) {
                failed++;
                messages.append("missing resource ").append(BASE).append(resource).append("; ");
                continue;
            }
            String jarSha = sha256(jar);
            shipped.put(resource, jarSha);
            try {
                if (Files.exists(target) && !overwrite) {
                    skipped++;
                    continue;
                }
                write(target, jar);
                record.put(resource, jarSha);
                copied++;
            } catch (Exception e) {
                failed++;
                messages.append(target.getFileName()).append(": ").append(e.getMessage()).append("; ");
                LOGGER.warn("[FoundationDataInstaller] Failed to install {}: {}", target, e.getMessage());
            }
        }
        writeManifest(server, dataVersion(shipped), record);
        return new InstallResult(copied, skipped, failed, messages.toString());
    }

    private static Map<String, Path> fileMap(MinecraftServer server) {
        Map<String, Path> map = new LinkedHashMap<>();
        map.put("events/foundation_events.json", EternalDataPaths.events(server).resolve("foundation_events.json"));
        map.put("vips/foundation_vips.json", EternalDataPaths.vips(server).resolve("foundation_vips.json"));
        map.put("buildings/foundation_buildings.json", EternalDataPaths.buildings(server).resolve("foundation_buildings.json"));
        map.put("civilizations/foundation_civilizations.json", EternalDataPaths.civilizations(server).resolve("foundation_civilizations.json"));
        map.put("religions/foundation_religions.json", EternalDataPaths.religions(server).resolve("foundation_religions.json"));
        map.put("technologies/foundation_technology_tree.json", EternalDataPaths.technologies(server).resolve("foundation_technology_tree.json"));
        map.put("climate/foundation_climate_events.json", EternalDataPaths.climate(server).resolve("foundation_climate_events.json"));
        map.put("templates/current_affairs_templates.json", EternalDataPaths.templates(server).resolve("current_affairs_templates.json"));
        map.put("names/foundation_names.json", EternalDataPaths.names(server).resolve("foundation_names.json"));
        map.put("data_source_config.json", EternalDataPaths.root(server).resolve("data_source_config.json"));
        // The history packs are not copied any more (step 14): HistoryGazetteer and the registers read them
        // from the mod jar, never from the world, so a world copy was only ever out of date.
        String[] populationPacks = {
            "index.json", "population_south_asia.json"
        };
        for (String pack : populationPacks) {
            map.put("population/" + pack, EternalDataPaths.population(server).resolve(pack));
        }
        // Authored divergence scenarios. Each is a complete "what if", keyed by trigger pin.
        // Expected to be sparse: a pin without a scenario still diverges, it simply has no
        // written consequences and the chronicle labels them as modelled.
        String[] scenarioPacks = {
            "what_if_mumbai_26_11.json"
        };
        for (String scenario : scenarioPacks) {
            map.put("scenarios/" + scenario, EternalDataPaths.scenarios(server).resolve(scenario));
        }
        return map;
    }

    /** The shipped files' world paths, for {@code /ee data status}. */
    public static Map<String, Path> shippedFiles(MinecraftServer server) {
        return server == null ? Map.of() : fileMap(server);
    }

    // ==================== helpers ====================

    private static byte[] readResource(String resource) {
        try (InputStream in = FoundationDataInstaller.class.getResourceAsStream(BASE + resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** First 8 hex digits of a checksum over every shipped file (name and checksum). */
    static String dataVersion(Map<String, String> shipped) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(shipped).forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    }

    private static Path manifestPath(MinecraftServer server) {
        return EternalDataPaths.root(server).resolve(MANIFEST);
    }

    private static Map<String, String> readManifest(MinecraftServer server) {
        Map<String, String> out = new TreeMap<>();
        Path p = manifestPath(server);
        if (!Files.isRegularFile(p)) {
            return out;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("files") && o.get("files").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("files").entrySet()) {
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[FoundationDataInstaller] {} is unreadable ({}); treating the world's files as unrecorded",
                p, e.getMessage());
        }
        return out;
    }

    private static void writeManifest(MinecraftServer server, String version, Map<String, String> record) {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("about", "Checksums of the data files as Eternal Engine installed them into this world. A file"
            + " whose checksum still matches is replaced when a new mod version ships a different one; a file you"
            + " changed is never overwritten.");
        o.addProperty("dataVersion", version);
        o.addProperty("updatedAt", LocalDateTime.now().toString());
        o.add("files", GSON.toJsonTree(new TreeMap<>(record)));
        try {
            write(manifestPath(server), GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("[FoundationDataInstaller] Could not write {}: {}", manifestPath(server), e.getMessage());
        }
    }

    // ==================== results ====================

    public enum FileState {
        /** The same as this mod version's copy. */
        CURRENT,
        /** Copied now: the world had no such file. */
        ADDED,
        /** Replaced now: unchanged since the mod installed it, and this mod version ships a different one. */
        UPDATED,
        /** Kept: the server owner changed it. */
        KEPT_EDITED,
        /** Kept: different, installed before the record existed (maybe an owner's edit). */
        KEPT_UNKNOWN,
        /** Could not be read or written. */
        FAILED
    }

    public record FileStatus(String resource, FileState state, String detail) {
    }

    public record RefreshResult(String dataVersion, List<FileStatus> files, boolean firstInstall) {
        public long count(FileState state) {
            return files.stream().filter(f -> f.state() == state).count();
        }

        public List<FileStatus> with(FileState state) {
            return files.stream().filter(f -> f.state() == state).toList();
        }

        public String summary() {
            return "data version " + dataVersion + ": " + files.size() + " files, " + count(FileState.CURRENT)
                + " current, " + count(FileState.ADDED) + " added, " + count(FileState.UPDATED) + " updated, "
                + (count(FileState.KEPT_EDITED) + count(FileState.KEPT_UNKNOWN)) + " kept as the world has them, "
                + count(FileState.FAILED) + " failed";
        }

        InstallResult asInstallResult() {
            StringBuilder m = new StringBuilder();
            for (FileStatus f : with(FileState.FAILED)) {
                m.append(f.resource()).append(": ").append(f.detail()).append("; ");
            }
            return new InstallResult((int) (count(FileState.ADDED) + count(FileState.UPDATED)),
                (int) (count(FileState.CURRENT) + count(FileState.KEPT_EDITED) + count(FileState.KEPT_UNKNOWN)),
                (int) count(FileState.FAILED), m.toString());
        }
    }

    public record InstallResult(int copied, int skipped, int failed, String message) {
        public String summary() {
            return "Foundation data install copied=" + copied + " skipped=" + skipped + " failed=" + failed
                + (message == null || message.isBlank() ? "" : " message=" + message);
        }
    }
}
