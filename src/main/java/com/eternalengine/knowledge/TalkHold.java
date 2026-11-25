package com.eternalengine.knowledge;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 21: a person stays while a player talks or trades with them.
 *
 * <p>Before this step a townsperson or crowd figure could vanish in the middle of a talk: the crowd re-arranged
 * itself while the player stood still (and a player who talks stands still), a year change took the year's
 * people away, a district check took a wanderer away. Each of those paths now asks {@link #busy} first and leaves a
 * busy person alone.
 *
 * <p>A person is busy from the moment a player opens the talk (right-click) until one of these:
 * <ul>
 *   <li>the player says goodbye (closes the talk or trade screen);</li>
 *   <li>the player is more than {@link #REACH} blocks away, in another world, or offline;</li>
 *   <li>the player has done nothing in the talk for {@link #IDLE_TICKS} ticks (2 minutes of game time).</li>
 * </ul>
 * One player holds at most one person; a new talk replaces the old hold. Everything runs on the server thread
 * (packets are handled there), the maps are concurrent only to be safe.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class TalkHold {

    /** The talk and trade screens close beyond 8 blocks, and the server answers no action from further away. */
    public static final double REACH = 8.0d;
    /** Two minutes of game time without an action in the talk. */
    public static final long IDLE_TICKS = 20L * 120L;

    private record Hold(UUID player, int entityId, long lastTick) {
    }

    private static final Map<Integer, Hold> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BY_PLAYER = new ConcurrentHashMap<>();

    private TalkHold() {
    }

    /** The player opened the talk, or did something in it: the person is held (again) from now. */
    public static void hold(@Nullable Player player, @Nullable HumanNpcEntity npc) {
        if (player == null || npc == null || npc.level().isClientSide) {
            return;
        }
        UUID id = player.getUUID();
        Integer before = BY_PLAYER.put(id, npc.getId());
        if (before != null && before != npc.getId()) {
            Hold old = BY_ENTITY.get(before);
            if (old != null && old.player().equals(id)) {
                BY_ENTITY.remove(before);
            }
        }
        BY_ENTITY.put(npc.getId(), new Hold(id, npc.getId(), npc.level().getGameTime()));
    }

    /** The talk is over (goodbye, logged out): the person is free to go. */
    public static void release(@Nullable UUID player) {
        if (player == null) {
            return;
        }
        Integer entity = BY_PLAYER.remove(player);
        if (entity != null) {
            Hold h = BY_ENTITY.get(entity);
            if (h != null && h.player().equals(player)) {
                BY_ENTITY.remove(entity);
            }
        }
    }

    /**
     * True while a player is talking or trading with this person. A hold whose player has gone (further than
     * {@link #REACH}, another world, offline) or has done nothing for {@link #IDLE_TICKS} is dropped here.
     */
    public static boolean busy(@Nullable HumanNpcEntity npc) {
        if (npc == null || BY_ENTITY.isEmpty()) {
            return false;
        }
        Hold h = BY_ENTITY.get(npc.getId());
        if (h == null) {
            return false;
        }
        Player p = npc.level().getPlayerByUUID(h.player());
        double distanceSq = p == null ? Double.MAX_VALUE : p.distanceToSqr(npc);
        if (holds(p != null && p.isAlive(), distanceSq, npc.level().getGameTime() - h.lastTick())) {
            return true;
        }
        BY_ENTITY.remove(npc.getId());
        BY_PLAYER.remove(h.player(), npc.getId());
        return false;
    }

    /** The player talking with this person, or null when nobody is. */
    @Nullable
    public static Player talker(@Nullable HumanNpcEntity npc) {
        if (!busy(npc)) {
            return null;
        }
        Hold h = BY_ENTITY.get(npc.getId());
        return h == null ? null : npc.level().getPlayerByUUID(h.player());
    }

    /** A cheap first look (no checks): whether anybody holds this entity id at all. */
    public static boolean mayBeHeld(int entityId) {
        return !BY_ENTITY.isEmpty() && BY_ENTITY.containsKey(entityId);
    }

    /** How many people are held right now, for the readouts. */
    public static int count() {
        return BY_ENTITY.size();
    }

    /** The rule itself: the player is here and alive, close enough, and has acted within the last 2 minutes. */
    static boolean holds(boolean playerHere, double distanceSq, long idleTicks) {
        return playerHere && distanceSq <= REACH * REACH && idleTicks >= 0 && idleTicks <= IDLE_TICKS;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        release(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) {
            TalkService.forget(sp.getUUID());
        }
    }

    /** The rule on made-up cases; null when it holds, else what broke. */
    public static String selfTest() {
        if (!holds(true, 3.0d * 3.0d, 0L)) {
            return "a player 3 blocks away who just spoke does not hold the person";
        }
        if (!holds(true, 8.0d * 8.0d, IDLE_TICKS)) {
            return "8 blocks and exactly 2 minutes should still hold";
        }
        if (holds(true, 8.1d * 8.1d, 0L)) {
            return "a player 8.1 blocks away still holds the person";
        }
        if (holds(true, 1.0d, IDLE_TICKS + 1L)) {
            return "a player idle for more than 2 minutes still holds the person";
        }
        if (holds(false, 1.0d, 0L)) {
            return "a player who left still holds the person";
        }
        return null;
    }
}
