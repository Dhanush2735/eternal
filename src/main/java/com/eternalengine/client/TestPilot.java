package com.eternalengine.client;

import com.eternalengine.EternalEngineMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns 226: the player pilot - <b>the test's own legs.</b> The user said the purpose is "for
 * testing at multiple areas and time other features and system", so the runtime suite does not
 * only teleport the player to each era stop: it walks them through it, the way a person would
 * arrive, so the systems that react to a walking player (crowds, spawners, talk, chunk streaming,
 * frame feel) are exercised while the frames are taken.
 *
 * <p>Rules, like everything that touches the player: it only moves when the test asks
 * ({@link #walk(double[][], double)}), <b>any movement key press cancels it instantly</b>
 * (the player always wins), and it walks with the world's own physics - walls stop it, the ground
 * holds it up, it hops one block when stuck. All state is written from the network thread and
 * consumed on the client tick thread; the conversion of relative waypoints to absolute ones
 * happens on the client tick, never on the netty thread.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID, value = Dist.CLIENT)
public final class TestPilot {

    /** Set from the packet thread; consumed (and cleared) on the client tick. */
    private static volatile double[][] pendingRel = null;
    private static volatile double pendingSpeed = 0.26d;

    private static volatile boolean walking = false;
    private static final List<double[]> targets = new ArrayList<>();
    private static double speed = 0.26d;
    private static int leg = 0;
    private static int stuckTicks = 0;
    private static double lastX;
    private static double lastZ;

    /**
     * Walk the given waypoints as (dx, dz) offsets from where the player stands when the walk
     * begins. Empty (or null) means stop now. Speed is blocks per tick (0.26 is a calm walk).
     */
    public static void walk(double[][] rel, double speedBlocksPerTick) {
        pendingRel = (rel == null || rel.length == 0) ? new double[0][2] : rel;
        pendingSpeed = speedBlocksPerTick;
    }

    /** Stop now. {@code quiet} suppresses the chat line (used when the test restarts the walk). */
    public static void stop(boolean quiet) {
        walking = false;
        if (!quiet) {
            chat("The test pilot stops.");
        }
    }

    public static boolean isWalking() {
        return walking;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                walking = false;
                pendingRel = null;
                return;
            }
            double[][] rel = pendingRel;
            if (rel != null) {
                // Begin (or restart) on the client thread: relative to where the player is NOW.
                pendingRel = null;
                synchronized (TestPilot.class) {
                    targets.clear();
                    if (rel.length == 0) {
                        walking = false;
                        return;
                    }
                    double x = mc.player.getX();
                    double z = mc.player.getZ();
                    for (double[] p : rel) {
                        targets.add(new double[] {x + p[0], z + p[1]});
                    }
                    speed = pendingSpeed;
                    leg = 0;
                    stuckTicks = 0;
                    lastX = x;
                    lastZ = z;
                    walking = true;
                }
                return;
            }
            if (!walking) {
                return;
            }
            if (keysPressed(mc)) {
                walking = false;
                chat("The test pilot stops: you took the controls.");
                return;
            }
            synchronized (TestPilot.class) {
                if (leg >= targets.size()) {
                    walking = false;
                    return;
                }
                double[] t = targets.get(leg);
                double dx = t[0] - mc.player.getX();
                double dz = t[1] - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                double moved = Math.sqrt(square(mc.player.getX() - lastX) + square(mc.player.getZ() - lastZ));
                lastX = mc.player.getX();
                lastZ = mc.player.getZ();
                if (dist < 1.5d) {
                    leg++;
                    stuckTicks = 0;
                    return;
                }
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                mc.player.setYRot(yaw);
                mc.player.yRotO = yaw;
                mc.player.yBodyRot = yaw;
                mc.player.yHeadRot = yaw;
                double vx = dx / dist * speed;
                double vz = dz / dist * speed;
                double vy = mc.player.getDeltaMovement().y;
                if (moved < 0.01d) {
                    stuckTicks++;
                    if (stuckTicks > 20) {
                        vy = 0.42d; // hop: a step, a slab, a fence
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                }
                mc.player.setDeltaMovement(vx, vy, vz);
            }
        } catch (Throwable t) {
            walking = false;
        }
    }

    private static double square(double v) {
        return v * v;
    }

    /** The player always wins: any movement key cancels the pilot. */
    private static boolean keysPressed(Minecraft mc) {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown();
    }

    private static void chat(String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.getChat().addMessage(Component.literal("[pilot] " + line));
        }
    }
}
