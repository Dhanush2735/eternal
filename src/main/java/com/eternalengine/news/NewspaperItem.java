package com.eternalengine.news;

import com.eternalengine.knowledge.KnowledgeDate;
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
 * Step 19d: a newspaper or a handwritten newsletter. It keeps its news on the item ({@link #TAG}), written the day it
 * was bought; right-click reads it.
 */
public class NewspaperItem extends Item {

    public static final String TAG = "EePaper";
    private final boolean handwritten;

    public NewspaperItem(boolean handwritten) {
        super(new Item.Properties().stacksTo(16));
        this.handwritten = handwritten;
    }

    public boolean handwritten() {
        return handwritten;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            NewsService.read(sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null && !t.getString("name").isEmpty()) {
            int date = t.getInt("date");
            return Component.literal(t.getString("name") + ", " + KnowledgeDate.shortLabel(date) + " "
                    + yearText(KnowledgeDate.year(date)));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null) {
            String place = t.getString("place");
            tip.add(Component.literal((place.isEmpty() ? "" : place + " · ") + KnowledgeDate.label(t.getInt("date")))
                    .withStyle(ChatFormatting.GRAY));
            int n = t.getList("stories", 10).size();
            tip.add(Component.literal(n + (n == 1 ? " story" : " stories") + (handwritten ? ", written by hand" : ""))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tip.add(Component.literal(handwritten ? "A blank newsletter" : "A blank paper").withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.literal("Right-click to read").withStyle(ChatFormatting.YELLOW));
    }

    static String yearText(int y) {
        return y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
    }
}
