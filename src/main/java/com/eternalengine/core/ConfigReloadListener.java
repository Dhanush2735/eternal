package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies the server config when a world loads it and when the file is edited while the game runs (step 14).
 *
 * <p>Forge re-reads an edited config file by itself, and code that asks for a value each time sees the new
 * one. The tick scheduler keeps its intervals instead, so without this an edited interval never took effect.
 * Forge sends these events on its config thread; the scheduler's maps are safe for that.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigReloadListener {

    private ConfigReloadListener() {
    }

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        apply(event.getConfig());
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
        apply(event.getConfig());
    }

    /**
     * {@code /ee reload}: read Eternal Engine's config files from disk again, the way Forge's own file watcher
     * does (load, correct a broken value, drop the cached values). The watcher only notices a file that was
     * changed in place; an editor that saves by writing a new file and renaming it was missed (tested in
     * step 14 with {@code sed -i}).
     *
     * @return one line per config file
     */
    public static List<String> reloadFromDisk() {
        List<String> out = new ArrayList<>();
        for (ModConfig.Type type : new ModConfig.Type[] {ModConfig.Type.COMMON, ModConfig.Type.SERVER}) {
            Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().get(type);
            if (configs == null) {
                continue;
            }
            for (ModConfig config : configs) {
                if (!EternalEngineMod.MOD_ID.equals(config.getModId())) {
                    continue;
                }
                if (!(config.getConfigData() instanceof CommentedFileConfig file)) {
                    out.add(config.getFileName() + ": not loaded (no world yet)");
                    continue;
                }
                try {
                    IConfigSpec<?> spec = config.getSpec();
                    file.load();
                    if (!spec.isCorrect(file)) {
                        spec.correct(file);
                        file.save();
                        out.add(config.getFileName() + ": read again (a wrong value was set back to its default)");
                    } else {
                        out.add(config.getFileName() + ": read again");
                    }
                    spec.afterReload();
                } catch (Exception e) {
                    out.add(config.getFileName() + ": could not be read (" + e.getMessage() + "); the values in use stay");
                }
            }
        }
        if (SimulationTickScheduler.isInitialized()) {
            SimulationTickScheduler.getInstance().reloadFromConfig();
        }
        return out;
    }

    private static void apply(ModConfig config) {
        if (config == null || config.getType() != ModConfig.Type.SERVER
                || !EternalEngineMod.MOD_ID.equals(config.getModId())
                || !SimulationTickScheduler.isInitialized()) {
            return;
        }
        SimulationTickScheduler.getInstance().reloadFromConfig();
    }
}
