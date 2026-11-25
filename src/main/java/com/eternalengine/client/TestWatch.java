package com.eternalengine.client;

import com.eternalengine.EternalEngineMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Turns 227: the frame watcher - the alternative to "the agent cannot feel the framerate". The
 * user: take alternatives for what I cannot do and do it inside {@code /ee test everything}. So
 * the run does not ask anyone how the game feels: it measures it. Every tick the sampler records
 * the frame rate and the client tick interval over a rolling five-second window, and every time
 * the test camera presses the shutter ({@code TestCamera}), the numbers around that frame are
 * appended to {@code etest_perf.txt} in the screenshots folder, next to the very pixels they
 * describe. Stutter cannot hide from a folder.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID, value = Dist.CLIENT)
public final class TestWatch {

    private static final int WINDOW = 100; // ticks, about five seconds
    private static final long[] tickNanos = new long[WINDOW];
    private static final int[] fpsSamples = new int[WINDOW];
    private static int cursor = 0;
    private static int count = 0;
    private static long lastTickNanos = 0L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                lastTickNanos = 0L;
                return;
            }
            long now = System.nanoTime();
            long interval = lastTickNanos == 0L ? 50_000_000L : now - lastTickNanos;
            lastTickNanos = now;
            if (interval > 2_000_000_000L) {
                interval = 50_000_000L; // a pause (window switch) is not a stutter
            }
            int fps = 0;
            try {
                fps = mc.getFps();
            } catch (Throwable ignored) {
            }
            tickNanos[cursor] = interval;
            fpsSamples[cursor] = fps;
            cursor = (cursor + 1) % WINDOW;
            if (count < WINDOW) {
                count++;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called by the test camera at the moment the shutter fires: the numbers around this frame are
     * appended beside the frame's own filename. Never throws.
     */
    public static void note(String shotName) {
        try {
            Minecraft mc = Minecraft.getInstance();
            File dir = new File(mc.gameDirectory, "screenshots");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                dir = mc.gameDirectory;
            }
            Path file = dir.toPath().resolve("etest_perf.txt");
            int fpsNow = 0;
            try {
                fpsNow = mc.getFps();
            } catch (Throwable ignored) {
            }
            long fpsSum = 0;
            int fpsMin = Integer.MAX_VALUE;
            int fpsSeen = 0;
            long tickSum = 0;
            long tickWorst = 0;
            int n = count;
            for (int i = 0; i < n; i++) {
                int f = fpsSamples[i];
                if (f > 0) {
                    fpsSum += f;
                    fpsSeen++;
                    if (f < fpsMin) {
                        fpsMin = f;
                    }
                }
                long t = tickNanos[i];
                tickSum += t;
                if (t > tickWorst) {
                    tickWorst = t;
                }
            }
            String fpsAvg = fpsSeen == 0 ? "?" : String.valueOf(fpsSum / fpsSeen);
            String fpsMinS = fpsSeen == 0 ? "?" : String.valueOf(fpsMin == Integer.MAX_VALUE ? 0 : fpsMin);
            String tickAvg = n == 0 ? "?" : String.valueOf((tickSum / n) / 1_000_000L);
            String tickWorstS = n == 0 ? "?" : String.valueOf(tickWorst / 1_000_000L);
            String line = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()) + "  "
                    + shotName + ".png | fps " + fpsNow + " now, " + fpsAvg + " avg, " + fpsMinS
                    + " min (last " + n + " ticks) | client tick " + tickAvg + " ms avg, "
                    + tickWorstS + " ms worst\n";
            boolean first = !Files.exists(file);
            if (first) {
                Files.write(file,
                        ("Eternal Engine frame report: one line per test shot, the numbers around"
                                + " that very frame. fps is Minecraft's own counter; client tick is"
                                + " the gap between client ticks (50 ms is healthy).\n")
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // read-only game folders: the chat line from the camera still stands
        }
    }
}
