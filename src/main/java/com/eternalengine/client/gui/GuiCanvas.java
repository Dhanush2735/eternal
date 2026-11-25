package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** Steps 19c to 19e: the painters draw through this with Minecraft's font and items. */
@OnlyIn(Dist.CLIENT)
public final class GuiCanvas implements Canvas {

    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Map<String, ItemStack> ROWS = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private final GuiGraphics g;
    private final Font font;

    public GuiCanvas(GuiGraphics g, Font font) {
        this.g = g;
        this.font = font;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        g.drawString(font, s, x, y, argb, shadow);
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    @Override
    public int width(String s) {
        return font.width(s);
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH) {
        ResourceLocation rl = TEXTURES.computeIfAbsent(texture, ResourceLocation::new);
        g.blit(rl, x, y, (float) u, (float) v, w, h, texW, texH);
    }

    @Override
    public void stack(com.eternalengine.money.TradeView.Stack s, int x, int y) {
        ItemStack st = stackOf(s);
        if (st.isEmpty()) {
            return;
        }
        g.renderItem(st, x, y);
        g.renderItemDecorations(font, st, x, y);
    }

    /** Step 24: the item a trade row shows (its data too, so a coin is named by its money), cached by its words. */
    static ItemStack stackOf(com.eternalengine.money.TradeView.Stack s) {
        if (s == null || s.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String key = s.item() + "#" + s.count() + "#" + (s.tag() == null ? "" : s.tag().toString());
        if (ROWS.size() > 512) {
            ROWS.clear();
        }
        return ROWS.computeIfAbsent(key, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(s.item());
            Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            ItemStack st = new ItemStack(it == null ? Items.BARRIER : it, Math.max(1, s.count()));
            if (s.tag() != null) {
                st.setTag(s.tag().copy());
            }
            return st;
        });
    }

    @Override
    public void item(String id, int count, int x, int y, int scale) {
        ItemStack stack = STACKS.computeIfAbsent(id + "#" + count, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            return new ItemStack(it == null ? Items.BARRIER : it, Math.max(1, count));
        });
        if (scale <= 1) {
            g.renderItem(stack, x, y);
            g.renderItemDecorations(font, stack, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
