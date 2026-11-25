package com.eternalengine.news;

import com.eternalengine.knowledge.BookService;
import net.minecraft.ChatFormatting;
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
 * Step 20: the history book. It holds nothing itself: right-click opens the player's own notes (what people told them
 * and what they read), kept with the world, so any copy is the same book.
 *
 * <p>Step 22: it is bought, not given, and it comes in the form people keep notes in where it is bought
 * ({@link BookForms}): a notebook, a papyrus roll, clay tablets, a tally stick... Every form reads the same notes.
 */
public class HistoryBookItem extends Item {

    private final BookForms.Form form;

    public HistoryBookItem(BookForms.Form form) {
        super(new Item.Properties().stacksTo(1));
        this.form = form;
    }

    public BookForms.Form form() {
        return form;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            BookService.open(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(form.lore).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(form.writing
                        ? "Your history book: every name you heard, what each person or paper said, and the day"
                        : "Your history book, as people here keep it: every name you heard, who said what, and the day")
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click to read").withStyle(ChatFormatting.YELLOW));
    }
}
