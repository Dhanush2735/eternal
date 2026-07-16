package com.eternalengine.vip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Central helper for marking real Minecraft entities as Eternal VIP bots.
 *
 * Tag format:
 *   ee_vip:<vipId>
 *
 * Any future villager/NPC spawning system should use this helper instead of
 * writing tags directly.
 */
public final class VipEntityHelper {
    public static final String VIP_TAG_PREFIX = "ee_vip:";

    private VipEntityHelper() {
    }

    public static boolean tagVipEntity(Entity entity, VipSlot vip) {
        if (entity == null || vip == null) {
            return false;
        }
        return tagVipEntity(entity, vip.vipId(), vip.name());
    }

    public static boolean tagVipEntity(Entity entity, String vipId, String displayName) {
        if (entity == null || vipId == null || vipId.isBlank()) {
            return false;
        }
        clearVipTag(entity);
        boolean added = entity.addTag(VIP_TAG_PREFIX + vipId.trim());
        if (displayName != null && !displayName.isBlank()) {
            entity.setCustomName(Component.literal(displayName));
            entity.setCustomNameVisible(true);
        }
        return added;
    }

    public static boolean isVipEntity(Entity entity) {
        return getVipId(entity) != null;
    }

    public static String getVipId(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(VIP_TAG_PREFIX)) {
                String id = tag.substring(VIP_TAG_PREFIX.length()).trim();
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }

    public static boolean clearVipTag(Entity entity) {
        if (entity == null) {
            return false;
        }
        boolean removed = false;
        for (String tag : java.util.List.copyOf(entity.getTags())) {
            if (tag != null && tag.startsWith(VIP_TAG_PREFIX)) {
                removed |= entity.removeTag(tag);
            }
        }
        return removed;
    }
}
