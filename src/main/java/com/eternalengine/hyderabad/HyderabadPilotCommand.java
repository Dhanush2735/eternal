package com.eternalengine.hyderabad;

import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.terrabridge.TerraBridge;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** Read-only live projection check, not city generation. */
public final class HyderabadPilotCommand {
    private HyderabadPilotCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("hyderabad")
            .then(Commands.literal("preview").requires(src -> src.hasPermission(2))
                .executes(ctx -> preview(ctx.getSource())));
    }

    private static int preview(CommandSourceStack source) {
        if (!SimulationClockManager.isInitialized()) {
            source.sendFailure(Component.literal("Hyderabad preview unavailable: simulation clock not initialized."));
            return 0;
        }
        int year = SimulationClockManager.getInstance().getCurrentYear();
        if (year != 2026) {
            source.sendFailure(Component.literal("Hyderabad preview is a 2026 OSM snapshot only; current year is " + year + ". No historical or future extrapolation."));
            return 0;
        }
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            source.sendFailure(Component.literal("Hyderabad preview unavailable: Terra++ is not ready."));
            return 0;
        }
        if (!bridge.isBoundTo(source.getLevel())) {
            source.sendFailure(Component.literal("Hyderabad preview refused: Terra++ is not bound to this level's generator."));
            return 0;
        }
        try {
            PilotProjectedPreview result = PilotProjectedPreview.bundled(year, (lat, lon) -> {
                double[] xz = bridge.fromGeo(lat, lon);
                // The public API can use an unrelated projection. This preview requires the active world transform.
                if (!"world".equals(bridge.fromGeoSource()) || !bridge.roundTrips(xz, lat, lon)) return null;
                return xz;
            });
            long roads = result.features.stream().filter(f -> f.road).count();
            long buildings = result.features.stream().filter(f -> f.building).count();
            long parts = result.features.stream().filter(f -> f.buildingPart).count();
            source.sendSuccess(() -> Component.literal("Hyderabad 2026 sample: " + result.features.size() + " OSM ways, "
                + result.vertices + " vertices; " + roads + " roads, " + buildings + " buildings, " + parts + " building parts (categories may overlap)."), false);
            source.sendSuccess(() -> Component.literal("World X " + result.minX + " to " + result.maxX + ", Z " + result.minZ + " to " + result.maxZ + ". Read-only preview: no blocks placed, changed or removed."), false);
            result.features.stream().filter(f -> "Charminar".equals(f.name) && !f.vertices.isEmpty()).findFirst().ifPresent(f -> {
                PilotProjectedPreview.Vertex v = f.vertices.get(0);
                source.sendSuccess(() -> Component.literal("Charminar mapped boundary vertex: X " + v.x + ", Z " + v.z + " (not a teleport point)."), false);
            });
            source.sendSuccess(() -> Component.literal("Tiny sample only; © OpenStreetMap contributors, ODbL: https://www.openstreetmap.org/copyright"), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Hyderabad preview refused: " + e.getMessage() + ". No world changes made."));
            return 0;
        }
    }
}
