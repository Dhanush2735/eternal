package com.eternalengine.news;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Step 19e: a map of the land around the town it was bought in, with the towns and roads of its year. Papers read
 * while carrying it mark their places on it ({@link #TAG}: title, centre, year, marks). Right-click opens it.
 */
public class MapItem extends Item {

    public static final String TAG = "EeMap";

    public MapItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            MapService.open(sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null && !t.getString("title").isEmpty()) {
            return Component.literal(t.getString("title"));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null) {
            tip.add(Component.literal("Towns and roads of " + NewspaperItem.yearText(t.getInt("year")))
                    .withStyle(ChatFormatting.GRAY));
            int papers = paperCount(t);
            tip.add(Component.literal(papers == 0 ? "No news marks yet" : "News marks from " + papers
                    + (papers == 1 ? " paper" : " papers")).withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.literal("Right-click to open").withStyle(ChatFormatting.YELLOW));
    }

    /** How many different papers (by name and date) have marked this map. */
    public static int paperCount(CompoundTag t) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (net.minecraft.nbt.Tag x : t.getList("marks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) x;
            seen.add(m.getString("paper") + "@" + m.getInt("date"));
        }
        return seen.size();
    }
}
