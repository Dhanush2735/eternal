package com.eternalengine.carto;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Historical Heraldry & Dynamic Banner Crest Registry.
 * Generates period-authentic heraldic banner crests with layered dye patterns
 * for civilizations, military cohorts, and sovereign throne rooms.
 */
public final class HeraldryRegistry {

    public record PatternLayer(ResourceKey<BannerPattern> pattern, DyeColor color) {}

    public record HeraldicCrest(
        String civId,
        DyeColor baseColor,
        String chargeName,
        List<PatternLayer> layers
    ) {}

    private final ConcurrentHashMap<String, HeraldicCrest> heraldryDatabase = new ConcurrentHashMap<>();

    public HeraldryRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Rome: Imperial crimson with golden sunburst / cross
        register("rome", DyeColor.RED, "SPQR Imperial Aquila", List.of(
            new PatternLayer(BannerPatterns.FLOWER, DyeColor.YELLOW),
            new PatternLayer(BannerPatterns.CROSS, DyeColor.YELLOW),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.YELLOW)
        ));

        // Carthage: Tyrian purple with silver crescent / lozenge
        register("carthage", DyeColor.PURPLE, "Tanit Crescent", List.of(
            new PatternLayer(BannerPatterns.RHOMBUS_MIDDLE, DyeColor.WHITE),
            new PatternLayer(BannerPatterns.STRIPE_MIDDLE, DyeColor.WHITE),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.WHITE)
        ));

        // Byzantium: Purple/Red with golden saltire
        register("byzantium", DyeColor.RED, "Paleologos Cross", List.of(
            new PatternLayer(BannerPatterns.STRAIGHT_CROSS, DyeColor.YELLOW),
            new PatternLayer(BannerPatterns.STRIPE_CENTER, DyeColor.YELLOW)
        ));

        // Caliphate / Islamic: Deep Green with silver crescent
        register("caliphate", DyeColor.GREEN, "Crescent & Star", List.of(
            new PatternLayer(BannerPatterns.CIRCLE_MIDDLE, DyeColor.WHITE),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.BLACK)
        ));

        // France: Royal blue with golden fleur-de-lis
        register("france", DyeColor.BLUE, "Fleur-de-lis", List.of(
            new PatternLayer(BannerPatterns.FLOWER, DyeColor.YELLOW),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.YELLOW)
        ));

        // England: St. George Red on White with Lions
        register("england", DyeColor.WHITE, "St. George Cross", List.of(
            new PatternLayer(BannerPatterns.STRAIGHT_CROSS, DyeColor.RED),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.RED)
        ));

        // China: Imperial Yellow with Vermilion Sun
        register("china", DyeColor.YELLOW, "Dragon Sunburst", List.of(
            new PatternLayer(BannerPatterns.CIRCLE_MIDDLE, DyeColor.RED),
            new PatternLayer(BannerPatterns.BORDER, DyeColor.RED)
        ));
    }

    public void register(String civId, DyeColor baseColor, String chargeName, List<PatternLayer> layers) {
        if (civId == null) return;
        heraldryDatabase.put(civId.toLowerCase(Locale.ROOT),
            new HeraldicCrest(civId, baseColor, chargeName, layers != null ? layers : List.of()));
    }

    /**
     * Builds an authentic vanilla ItemStack banner with historical pattern NBT.
     */
    public ItemStack createBannerItem(String civId) {
        String key = civId != null ? civId.toLowerCase(Locale.ROOT) : "rome";
        HeraldicCrest crest = heraldryDatabase.getOrDefault(key, heraldryDatabase.get("rome"));

        ItemStack banner = switch (crest.baseColor()) {
            case RED -> new ItemStack(Items.RED_BANNER);
            case PURPLE -> new ItemStack(Items.PURPLE_BANNER);
            case BLUE -> new ItemStack(Items.BLUE_BANNER);
            case GREEN -> new ItemStack(Items.GREEN_BANNER);
            case YELLOW -> new ItemStack(Items.YELLOW_BANNER);
            case BLACK -> new ItemStack(Items.BLACK_BANNER);
            default -> new ItemStack(Items.WHITE_BANNER);
        };

        // Construct Banner BlockEntityTag Patterns NBT
        CompoundTag blockEntityTag = new CompoundTag();
        ListTag patternsList = new ListTag();

        for (PatternLayer layer : crest.layers()) {
            CompoundTag patternTag = new CompoundTag();
            // Get banner pattern asset ID
            String patternCode = layer.pattern() != null
                ? layer.pattern().location().getPath()
                : "base";
            patternTag.putString("Pattern", patternCode);
            patternTag.putInt("Color", layer.color().getId());
            patternsList.add(patternTag);
        }

        blockEntityTag.put("Patterns", patternsList);
        banner.getOrCreateTag().put("BlockEntityTag", blockEntityTag);
        banner.setHoverName(net.minecraft.network.chat.Component.literal("§6" + crest.chargeName() + " (" + key.toUpperCase(Locale.ROOT) + ")"));

        return banner;
    }

    public HeraldicCrest getCrest(String civId) {
        if (civId == null) return null;
        return heraldryDatabase.get(civId.toLowerCase(Locale.ROOT));
    }

    public Map<String, HeraldicCrest> getAllCrests() {
        return Collections.unmodifiableMap(heraldryDatabase);
    }
}
