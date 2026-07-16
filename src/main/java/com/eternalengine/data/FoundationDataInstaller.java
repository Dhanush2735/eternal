package com.eternalengine.data;

import com.eternalengine.EternalEngineMod;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Installs bundled starter JSON data into the world folder.
 */
public final class FoundationDataInstaller {
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    private static final String BASE = "/data/eternalengine/eternal/";

    private FoundationDataInstaller() {
    }

    public static InstallResult install(MinecraftServer server, boolean overwrite) {
        if (server == null) {
            return new InstallResult(0, 0, 1, "server is null");
        }
        Map<String, Path> files = fileMap(server);
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        StringBuilder messages = new StringBuilder();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String resource = BASE + entry.getKey();
            Path target = entry.getValue();
            try (InputStream in = FoundationDataInstaller.class.getResourceAsStream(resource)) {
                if (in == null) {
                    failed++;
                    messages.append("missing resource ").append(resource).append("; ");
                    continue;
                }
                Files.createDirectories(target.getParent());
                if (Files.exists(target) && !overwrite) {
                    skipped++;
                    continue;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception e) {
                failed++;
                messages.append(target.getFileName()).append(": ").append(e.getMessage()).append("; ");
                LOGGER.warn("[FoundationDataInstaller] Failed to install {}: {}", target, e.getMessage());
            }
        }
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
        return map;
    }

    public record InstallResult(int copied, int skipped, int failed, String message) {
        public String summary() {
            return "Foundation data install copied=" + copied + " skipped=" + skipped + " failed=" + failed
                + (message == null || message.isBlank() ? "" : " message=" + message);
        }
    }
}
