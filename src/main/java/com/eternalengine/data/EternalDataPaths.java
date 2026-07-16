package com.eternalengine.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * World data paths for Eternal Engine.
 */
public final class EternalDataPaths {
    private EternalDataPaths() {
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("eternalengine");
    }

    public static Path events(MinecraftServer server) { return root(server).resolve("events"); }
    public static Path vips(MinecraftServer server) { return root(server).resolve("vips"); }
    public static Path buildings(MinecraftServer server) { return root(server).resolve("buildings"); }
    public static Path civilizations(MinecraftServer server) { return root(server).resolve("civilizations"); }
    public static Path religions(MinecraftServer server) { return root(server).resolve("religions"); }
    public static Path technologies(MinecraftServer server) { return root(server).resolve("technologies"); }
    public static Path climate(MinecraftServer server) { return root(server).resolve("climate"); }
    public static Path templates(MinecraftServer server) { return root(server).resolve("templates"); }
    public static Path names(MinecraftServer server) { return root(server).resolve("names"); }
}
