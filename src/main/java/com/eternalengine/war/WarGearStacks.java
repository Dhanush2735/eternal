package com.eternalengine.war;

import com.eternalengine.compatibility.ModCompatibilityChecker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Step 45 part 6 (A13): <b>the wiring</b> — the only class in this step that touches Minecraft.
 *
 * <p>{@link WarGear} decides everything and touches nothing; this class turns its answers into
 * stacks and puts them in hands. Keeping the two apart is what lets 2,427 checks run in four
 * seconds with no classpath at all.
 *
 * <h2>Replacing the hard-coded items</h2>
 *
 * <p>Eighteen sites across six files handed out {@code Items.IRON_SWORD}, {@code Items.BOW},
 * {@code Items.CROSSBOW} and {@code Items.IRON_AXE} with no regard for where or when. Every one
 * of them now goes through {@link #kitFor(LivingEntity, String, int)}, which asks the armoury
 * what had reached this place by this year and the resolver which item stands for it.
 *
 * <h2>The crafting gate, and why it works the way it does</h2>
 *
 * <p><b>Forge has no "cancel this craft" event.</b> {@code ItemCraftedEvent} fires <i>after</i>
 * the item has been taken, and there is no hook that can refuse one. So the gate does the two
 * things that are actually possible:
 *
 * <ol>
 *   <li>It <b>empties the result slot</b> while it holds an item that may not exist here yet
 *       ({@link #policeResultSlot}). The recipe still produces the item; it simply cannot be
 *       taken. This is the standard workaround and it is honest about what it is.</li>
 *   <li>It <b>refuses the gun mods' own crafting blocks</b> before their year
 *       ({@link #blockedTable}). A gun bench opened in 1400 says so and shuts.</li>
 * </ol>
 *
 * <p>A player who somehow gets a refused item — a creative player, an admin, another mod — is not
 * punished: the item still works. The gate exists so the <i>world's</i> soldiers and the world's
 * crafting obey the year, not to police the player's inventory.
 */
public final class WarGearStacks {

    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** A stand-in item for when nothing resolves at all. Never null-returned to a caller. */
    private static final String LAST_RESORT = "minecraft:wooden_sword";

    private WarGearStacks() {
    }

    // ------------------------------------------------------------------------------------------ items

    /** The item for an id, or null when no such item exists in this installation. */
    @Nullable
    public static Item item(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A stack for a resolution.
     *
     * <p>A <b>tag</b> is resolved by taking the first item in it, which is why part 5 prefers
     * tags: a mod renaming its registry does not change what the tag contains. The stack is named
     * after the local name when there is one, so a talwar in Agra is called a talwar.
     */
    public static ItemStack stack(@Nullable WeaponItems.Resolution r) {
        if (r == null) return fallback();
        Item found = null;
        if (r.isTag()) {
            found = firstInTag(r.id());
        } else {
            found = item(r.id());
        }
        if (found == null) {
            LOG.debug("[EE war] {} resolved to nothing; using {}", r.id(), LAST_RESORT);
            return fallback();
        }
        ItemStack s = new ItemStack(found);
        if (r.name() != null && !r.name().isBlank()) {
            s.setHoverName(Component.literal(r.name()));
            CompoundTag tag = s.getOrCreateTag();
            tag.putBoolean("EternalLocalName", true);
        }
        return s;
    }

    /** The first item a tag contains, or null when the tag is empty or absent. */
    @Nullable
    public static Item firstInTag(String tagId) {
        String id = tagId == null ? "" : tagId;
        Optional<Item> cached = FIRST_TAG_ITEM.get(id);
        if (cached != null) {
            return cached.orElse(null);
        }
        Item found = null;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(
                    id.startsWith("#") ? id.substring(1) : id);
            if (rl != null) {
                TagKey<Item> key = TagKey.create(net.minecraft.core.registries.Registries.ITEM, rl);
                for (Item i : ForgeRegistries.ITEMS) {
                    ItemStack probe = new ItemStack(i);
                    if (!probe.isEmpty() && probe.is(key)) {
                        found = i;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            found = null;
        }
        FIRST_TAG_ITEM.put(id, Optional.ofNullable(found));
        return found;
    }

    /** Tag lookups scan the item registry once and are then remembered (a scan is not free). */
    private static final Map<String, Optional<Item>> FIRST_TAG_ITEM = new HashMap<>();

    private static ItemStack fallback() {
        Item i = item(LAST_RESORT);
        return i == null ? ItemStack.EMPTY : new ItemStack(i);
    }

    /** The modids that are loaded, for the resolver's "is this mod here?" question. */
    public static Set<String> loadedModIds() {
        return ModCompatibilityChecker.loadedWeaponModIds();
    }

    // ------------------------------------------------------------------------------------------ equipping

    /**
     * The kit for a job at where an entity stands, in a year.
     *
     * <p>This is the one lookup the eighteen hard-coded sites now share.
     */
    public static WarGear.Kit kitFor(LivingEntity entity, String job, int year) {
        double[] place = placeOf(entity);
        if (place == null) return WarGear.get().forRole(job, "EUR", year, 0, 0, loadedModIds());
        String area = areaOf(place[0], place[1], year);
        return WarGear.get().forRole(job, area == null ? "EUR" : area, year, place[0], place[1],
                loadedModIds());
    }

    /** Put the kit on an entity, replacing the eighteen hand-written equipment blocks. */
    public static void equip(LivingEntity entity, String job, int year) {
        if (entity == null) return;
        WarGear.Kit kit = kitFor(entity, job, year);
        if (kit.empty()) {
            LOG.debug("[EE war] no kit for {} in {}", job, year);
            return;
        }
        WarGear.Slot melee = kit.slots().get("melee");
        WarGear.Slot missile = kit.slots().get("missile");
        WarGear.Slot mount = kit.slots().get("mount");
        WarGear.Slot protection = kit.slots().get("protection");

        WarGear.Slot held = melee != null ? melee : (missile != null ? missile : mount);
        if (held != null) {
            entity.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    stack(held.item()));
        }
        if (protection != null && isWearable(protection.kind())) {
            putOn(entity, protection.kind(), stack(protection.item()));
        } else if (protection != null) {
            entity.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                    stack(protection.item()));
        }
        // A shield in the off hand, whatever the main hand holds: this is the shape a guard has.
        if (protection != null && "shield".equals(protection.kind().id())) {
            entity.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                    stack(protection.item()));
        }
    }

    private static boolean isWearable(Armoury.Kind kind) {
        return kind != null && !"shield".equals(kind.id());
    }

    private static void putOn(LivingEntity entity, Armoury.Kind kind, ItemStack stack) {
        switch (kind.id()) {
            case "helmet" -> entity.setItemSlot(EquipmentSlot.HEAD, stack);
            case "mail", "plate" -> entity.setItemSlot(EquipmentSlot.CHEST, stack);
            default -> entity.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);
        }
    }

    // ------------------------------------------------------------------------------------------ crafting

    /**
     * Whether this item may be crafted here and now, with the reason.
     *
     * @return the reason it may not, or null when it may
     */
    @Nullable
    public static String craftRefusal(ItemStack stack, double lat, double lon, int year,
                                      String area) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) return null;
        WarGear.Verdict v = WarGear.get().verdictFor(rl.toString(), area, year, lat, lon,
                loadedModIds());
        return v.allowed() ? null : v.why();
    }

    /**
     * Empty the result slot while it holds an item that may not exist yet.
     *
     * <p><b>This is a workaround, and the javadoc says why it has to be.</b> Forge fires
     * {@code ItemCraftedEvent} only after the item has been taken, and there is no hook to refuse
     * a craft before that. So the slot is emptied while the recipe would produce a weapon that
     * has not reached this place: the recipe exists, but it cannot be taken here.
     */
    public static boolean policeResultSlot(AbstractContainerMenu menu, double lat, double lon,
                                           int year, String area) {
        if (menu == null) return false;
        for (Slot slot : menu.slots) {
            if (!(slot instanceof ResultSlot)) continue;
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            String why = craftRefusal(s, lat, lon, year, area);
            if (why != null) {
                slot.set(ItemStack.EMPTY);
                LOG.debug("[EE war] emptied a result slot holding {} — {}",
                        ForgeRegistries.ITEMS.getKey(s.getItem()), why);
                return true;
            }
        }
        return false;
    }

    /** The mods whose own crafting blocks are gated by the year, with the block they add. */
    public static final List<String> GUN_TABLES = List.of("tacz", "scorched_guns");

    /**
     * Whether a gun mod's own crafting block is usable in this year.
     *
     * <p>These mods ship a bench of their own, and the bench is the front door: leaving it open
     * in 1400 would hand over everything behind it. The rifle reaches Europe in 1866 and that is
     * the year the bench opens.
     */
    @Nullable
    public static String blockedTable(String modId, double lat, double lon, int year, String area) {
        if (!GUN_TABLES.contains(modId)) return null;
        Armoury.Kind rifle = Armoury.get().kind("rifle");
        if (rifle == null) return null;
        int arrives = Armoury.get().arrivalOf(rifle, lat, lon, area);
        if (arrives == WarGear.NEVER) {
            return "the rifle never reaches this place";
        }
        return year < arrives ? "the gun bench does not open until " + WarGear.label(arrives) : null;
    }

    /** Whether a gun mod's bench is open, asked in the form a caller wants. */
    public static boolean tableOpen(String modId, double lat, double lon, int year, String area) {
        return GUN_TABLES.contains(modId) && blockedTable(modId, lat, lon, year, area) == null;
    }

    // ------------------------------------------------------------------------------------------ place

    /**
     * The place an entity stands, as latitude and longitude.
     *
     * <p>Step 37 established one shared timeline and one shared coordinate frame, so the war
     * system reads the place the way the rest of the mod does: through
     * {@code TerraCoordinateHelper}, the same helper the crowd and the news use.
     */
    @Nullable
    public static double[] placeOf(LivingEntity entity) {
        if (entity == null) return null;
        var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(
                entity.blockPosition());
        return geo == null ? null : new double[]{geo.latitude(), geo.longitude()};
    }

    /** The year the world is in, from the one shared clock, or 0 before it starts. */
    public static int currentYear() {
        return com.eternalengine.clock.SimulationClockManager.isInitialized()
                ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                : 0;
    }

    /** The area an entity stands in, or null when the place cannot be read. */
    @Nullable
    public static String areaOf(double lat, double lon, int year) {
        var p = com.eternalengine.money.ProductAtlas.get().place(lat, lon, year);
        return p == null ? null : p.area();
    }
}
