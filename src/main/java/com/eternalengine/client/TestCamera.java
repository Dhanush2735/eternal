package com.eternalengine.client;

import net.minecraft.client.Minecraft;

/**
 * Turn 223: the test camera. The runtime suite decides when and what; this class presses the
 * shutter, on the client's own render thread (the packet handler runs it there), into the game's
 * own screenshots folder with the suite's name. Vanilla's {@code Screenshot.grab} does the work:
 * the same call the F2 key makes, so the picture is exactly what the player would have seen.
 *
 * <p>A failure (no world yet, a window with no surface) is printed to the chat and never thrown:
 * the route records the miss in its report and carries on to the next stop.
 */
public final class TestCamera {

    private TestCamera() {
    }

    /** {@code screenshots/<name>.png}, as the game writes it. */
    public static void capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        String safe = name == null || name.isBlank() ? "etest_unnamed" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            // The frame watcher records the numbers around this very frame (turn 227).
            com.eternalengine.client.TestWatch.note(safe);
            // The build named the real overload: grab(File, String, RenderTarget, Consumer).
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, safe,
                    mc.getMainRenderTarget(), (message) -> {
                    });
        } catch (Throwable t) {
            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                    "Test camera could not shoot " + safe + ": " + t.getMessage()));
        }
    }
}
