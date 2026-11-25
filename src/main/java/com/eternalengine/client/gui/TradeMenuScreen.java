package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.TradeMenuPainter;
import com.eternalengine.client.gui.paint.TradePainter;
import com.eternalengine.money.TradeMenu;
import com.eternalengine.money.TradeView;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TalkActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 24: Minecraft's trade screen with a townsperson (the villager's trade screen, with real slots). The trades are
 * on the left; clicking one asks the server to fill the two slots from the inventory; the thing to take appears on the
 * right when the slots pay for it. Above it the trader's words, below it what the slots do and your money; "‹ Back to
 * ..." returns to the talk. It closes when the person leaves or the player walks away (the server says so).
 */
@OnlyIn(Dist.CLIENT)
public class TradeMenuScreen extends AbstractContainerScreen<TradeMenu> {

    private static int lastEntity = -1;
    private static long lastClosed;

    private final TradeMenuPainter.State state = new TradeMenuPainter.State();
    private List<TradePainter.Hot> hots = List.of();
    private int lastSelected = -2;

    public TradeMenuScreen(TradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TradeMenuPainter.W;
        this.imageHeight = TradeMenuPainter.H;
        this.inventoryLabelX = 107;
    }

    public int entityId() {
        return menu.entityId();
    }

    /** True for a few seconds after this person's trade screen closed (so "Lakshmi has left." can still show). */
    public static boolean closedRecently(int entityId) {
        return entityId == lastEntity && System.currentTimeMillis() - lastClosed < 3000L;
    }

    /** A new view from the server: the trades, the words, what the slots do. */
    public void update(TradeView v) {
        menu.setView(v);
        state.scroll = Math.max(0, Math.min(state.scroll, TradeMenuPainter.maxScroll(v)));
        if (v.selected >= 0 && v.selected != lastSelected
                && (v.selected < state.scroll || v.selected >= state.scroll + TradeMenuPainter.ROWS)) {
            state.scroll = Math.max(0, Math.min(v.selected - TradeMenuPainter.ROWS / 2, TradeMenuPainter.maxScroll(v)));
        }
        lastSelected = v.selected;
        if (minecraft != null && v.sound == 1) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_YES, 1.0f));
        } else if (minecraft != null && v.sound == 2) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        hots = TradeMenuPainter.paint(new GuiCanvas(g, font), menu.view(), state, leftPos, topPos, width, height, mouseX,
                mouseY, Component.translatable("merchant.trades").getString(), playerInventoryTitle.getString());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // The painter draws the labels where Minecraft's trade screen has them.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        rowTooltip(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Over a trade: the item under the mouse (as in Minecraft), else what the trade is and costs. */
    private void rowTooltip(GuiGraphics g, int mx, int my) {
        TradeView v = menu.view();
        int idx = TradeMenuPainter.rowAt(v, state, leftPos, topPos, mx, my);
        if (idx < 0) {
            return;
        }
        TradeView.Row r = v.rows.get(idx);
        TradeView.Stack over = mx >= leftPos + 10 && mx < leftPos + 26 ? r.cost1()
                : mx >= leftPos + 40 && mx < leftPos + 56 ? r.cost2()
                : mx >= leftPos + 73 && mx < leftPos + 89 ? r.result() : null;
        if (over != null && !over.isEmpty()) {
            ItemStack st = GuiCanvas.stackOf(over);
            if (!st.isEmpty()) {
                g.renderTooltip(font, st, mx, my);
                return;
            }
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(r.name()).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        if (!r.price().isEmpty()) {
            String head = switch (r.kind()) {
                case SELL -> "He pays: ";
                case CHANGE, MAKE, BREAK -> "";
                default -> "Price: ";
            };
            lines.addAll(font.split(Component.literal(head + r.price()).withStyle(ChatFormatting.GOLD), 220));
        }
        if (!r.lore().isEmpty()) {
            lines.addAll(font.split(Component.literal(r.lore()).withStyle(ChatFormatting.GRAY), 220));
        }
        if (r.owned()) {
            lines.add(Component.literal("You have this already.").withStyle(ChatFormatting.RED).getVisualOrderText());
        } else if (r.left() == 0) {
            lines.add(Component.literal("Not today.").withStyle(ChatFormatting.RED).getVisualOrderText());
        } else if (r.left() > 0) {
            lines.add(Component.literal(r.left() + " left today.").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        g.renderTooltip(font, lines, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        state.dragging = false;
        if (button == 0) {
            for (TradePainter.Hot h : hots) {
                if (!h.contains(mx, my)) {
                    continue;
                }
                switch (h.action()) {
                    case "row" -> {
                        int i = Integer.parseInt(h.arg());
                        if (minecraft != null && minecraft.gameMode != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                        }
                        menu.view().selected = i;
                        lastSelected = i;
                        return true;
                    }
                    case "back" -> {
                        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(menu.entityId(), "show", "", ""));
                        if (minecraft != null) {
                            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                        }
                        return true;
                    }
                    case "scroll" -> {
                        state.dragging = true;
                        return true;
                    }
                    default -> {
                        // nothing else is clickable
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int max = TradeMenuPainter.maxScroll(menu.view());
        if (state.dragging && max > 0) {
            int top = topPos + 18;
            float f = ((float) my - top - 13.5f) / (139.0f - 27.0f);
            state.scroll = Math.max(0, Math.min(max, (int) (f * max + 0.5f)));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        state.dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = TradeMenuPainter.maxScroll(menu.view());
        if (max > 0) {
            state.scroll = Math.max(0, Math.min(max, (int) (state.scroll - delta)));
        }
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        lastEntity = menu.entityId();
        lastClosed = System.currentTimeMillis();
    }
}
