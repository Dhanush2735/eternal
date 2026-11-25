package com.eternalengine.money;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Step 23b: one piece of money in the hand: a coin, a note, cowries, cacao beans, cut silver. The item is the look; the
 * money and the value ride on the stack ({@value #TAG}: money id, value in the money's smallest unit, the piece's name
 * and the money's name), so equal pieces stack and a piece keeps its name wherever it goes. A piece with no money on
 * it (from the creative menu) is worth nothing.
 */
public class MoneyItem extends Item {

    public static final String TAG = "EeMoney";

    private final Denominations.Form form;

    public MoneyItem(Denominations.Form form) {
        super(new Item.Properties().stacksTo(64));
        this.form = form;
    }

    public Denominations.Form form() {
        return form;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null && !t.getString("label").isEmpty()) {
            return Component.literal(t.getString("label"));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(form.lore).withStyle(ChatFormatting.GRAY));
        CompoundTag t = stack.getTagElement(TAG);
        if (t == null || t.getLong("value") <= 0) {
            tip.add(Component.literal("No money is struck on it: worth nothing").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!t.getString("of").isEmpty()) {
            tip.add(Component.literal("Money: " + t.getString("of")).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.literal("Pay with it where this money is taken; a money-changer changes it")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** True for a piece that carries money. */
    public static boolean isMoney(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof MoneyItem && value(s) > 0 && !moneyId(s).isEmpty();
    }

    /** The money id on a piece ("" when none). */
    public static String moneyId(ItemStack s) {
        CompoundTag t = s.getTagElement(TAG);
        return t == null ? "" : t.getString("money");
    }

    /** The value of one piece in its money's smallest unit (0 when none). */
    public static long value(ItemStack s) {
        CompoundTag t = s.getTagElement(TAG);
        return t == null ? 0L : t.getLong("value");
    }

    /** A stack of {@code count} pieces (at most 64) of one money. */
    public static ItemStack make(MoneyTable.Money m, Denominations.Piece p, int count) {
        ItemStack s = new ItemStack(MoneyItems.item(p.form()), Math.max(1, Math.min(64, count)));
        CompoundTag t = s.getOrCreateTagElement(TAG);
        t.putString("money", m.id());
        t.putLong("value", p.value());
        t.putString("label", Denominations.itemName(m, p));
        t.putString("of", m.name());
        return s;
    }
}
