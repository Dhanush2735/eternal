package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.TradeMenuPainter;
import com.eternalengine.knowledge.TradePanelView;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TalkActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The trade screen on Minecraft's own villager trading picture
 * ({@code villager2.png}, the reference the owner gave): the "Trades" list on
 * the left with its buttons, the scrollbar, the two slots to pay with, the
 * arrow and the slot to take from, "Inventory" and the player's inventory on
 * the right — the same picture {@link TradeMenuPainter} draws for the real
 * trade menu, at the same places. Above it the dark box with what he says,
 * below it the status box, and "‹ Back to …" at the inventory row returns to
 * the talk, where the trade stands already made.
 *
 * <p>The era goods are words ("bronze sickle", "horseshoes"), not Minecraft
 * items, so each slot carries the good's first letter where the villager's
 * screen carries a picture; the full names and prices stand under the middle
 * slots, in the row tooltips, and in the says and status boxes. The inventory
 * shows the player's real carried items (drawn, not clickable — this screen
 * is not a container). The title and level bar follow the reference —
 * profession, first level ("… - Novice"), the empty bar under it.
 */
public class TradePanelScreen extends net.minecraft.client.gui.screens.Screen {

    private static final ResourceLocation VILLAGER = new ResourceLocation(TradeMenuPainter.VILLAGER);
    private static final ResourceLocation WIDGETS = new ResourceLocation(TradeMenuPainter.WIDGETS);

    private static final int DARK = 0xE0101014;
    private static final int LABEL = 0xFF404040;
    private static final int GOLD = 0xFFFFFF55;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int DIM = 0xFFA8A8B0;
    private static final int STATUS = 0xFFFFE08A;
    private static final int BACK = 0xFF2B4FA0;
    private static final int BACK_HOVER = 0xFF4F7FE0;
    private static final int INK = 0xFF202020;

    private static final int W = 276;
    private static final int GREY_H = 166;
    private static final int ROW_H = 20;
    private static final int ROWS = 7;

    private TradePanelView view;
    private String said;
    private int selRow = 0;
    private int x, greyY, rowsY, backX, backY, backW;

    public TradePanelScreen(TradePanelView view) {
        super(Component.literal(view.who + view.title));
        this.view = view;
        this.said = view.said;
    }

    public int entityId() {
        return view.entityId;
    }

    public void update(TradePanelView next) {
        this.view = next;
        this.said = next.said;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        x = (width - W) / 2;

        // What he says, and the date at its right end. His name stands in yellow,
        // the rest in white, as the prototype has it. The first line leaves room
        // for the date; the rest run the full width. The date's place is
        // dropped in this corner ("17-18 September 1948" only): with the
        // place kept, the first line held one word alone (owner photo
        // 2026-11-16_22.24.06, "Setty" by itself).
        String dateText = view.date == null ? "" : view.date;
        int dot = dateText.indexOf('·');
        if (dot >= 0) {
            dateText = dateText.substring(dot + 1).strip();
        }
        int dateW = font.width(dateText);
        int firstW = Math.max(60, W - 18 - dateW);
        List<String> saidLines = ScreenText.wrap(view.who + said, i -> i == 0 ? firstW : W - 16, font::width);
        if (saidLines.size() > 5) {
            saidLines = saidLines.subList(0, 5);
        }
        int saysH = 6 + saidLines.size() * 10 + 4;
        int statusH = 6 + (ScreenText.wrap(view.status, W - 16, font::width).size()
                + ScreenText.wrap(view.pocket, W - 16, font::width).size()) * 10 + 4;
        int top = (height - (saysH + 2 + GREY_H + 2 + statusH)) / 2;
        int saysY = top;
        greyY = top + saysH + 2;
        int statusY = greyY + GREY_H + 2;

        g.fill(x, saysY, x + W, saysY + saysH, DARK);
        outline(g, x, saysY, x + W, saysY + saysH, 0xFF000000);
        int ty = saysY + 4;
        int whoLeft = view.who.length();
        for (String line : saidLines) {
            int lx = x + 5;
            String rest = line;
            if (whoLeft > 0) {
                int take = Math.min(whoLeft, rest.length());
                g.drawString(font, rest.substring(0, take), lx, ty, GOLD, false);
                lx += font.width(rest.substring(0, take));
                rest = rest.substring(take);
                whoLeft -= take;
            }
            if (!rest.isEmpty()) {
                g.drawString(font, rest, lx, ty, TEXT, false);
            }
            ty += 10;
        }
        g.drawString(font, dateText, x + W - 5 - dateW, saysY + 4, DIM, false);

        // Minecraft's villager trading picture itself, 276x166 — the slot
        // frames, the big arrow and the inventory grid are baked into it.
        g.blit(VILLAGER, x, greyY, 0.0f, 0.0f, W, GREY_H, 512, 256);

        // The labels where Minecraft has them. Minecraft's title is the
        // profession with its level ("Butcher - Novice"), as in the
        // reference; the era counter's traders stand at the first level.
        centered(g, "Trades", x + 53, greyY + 6, LABEL);
        String title = view.title == null || view.title.isEmpty() ? "" : view.title + " - Novice";
        centered(g, fit(title, 150), x + 187, greyY + 6, LABEL);

        // Minecraft's level bar under the title (villager2.png at 0,186,
        // 102x5), at 136,16 as MerchantScreen draws it. Novice, no fill yet.
        g.blit(VILLAGER, x + 136, greyY + 16, 0.0f, 186.0f, 102, 5, 512, 256);
        String invLabel = "Inventory";
        g.drawString(font, invLabel, x + 107, greyY + 72, LABEL, false);

        // Back to the talk, at the right of the inventory label. The back
        // text is long ("‹ Back to <name>"), so it is fitted to the room left
        // of it and shortened to "‹ Back" where it would overlap the label —
        // the same rule TradeMenuPainter uses.
        int room = W - 8 - (107 + font.width(invLabel) + 10);
        String backText = fit(view.back, room);
        if (font.width(backText) > room || (backText.endsWith("…") && font.width("‹ Back") < room)) {
            backText = "‹ Back";
        }
        backW = font.width(backText);
        backX = x + W - 8 - backW;
        backY = greyY + 72;
        boolean overBack = mouseX >= backX && mouseX < backX + backW && mouseY >= backY && mouseY < backY + 10;
        g.drawString(font, backText, backX, backY, overBack ? BACK_HOVER : BACK, false);
        if (overBack) {
            g.fill(backX, backY + 9, backX + backW, backY + 10, BACK_HOVER);
        }

        // The trades on the left, as buttons of Minecraft's widgets picture —
        // one for each trade he holds (at most three); the rest of the list
        // stands empty, as in Minecraft. Each holds what he gives, the arrow,
        // and what he takes, as first letters where Minecraft has pictures.
        rowsY = greyY + 18;
        for (int i = 0; i < ROWS && i < view.sell.size(); i++) {
            if (view.sell.get(i) == null || view.sell.get(i).isEmpty()) {
                continue;
            }
            int ry = rowsY + i * ROW_H;
            int rx = x + 5;
            boolean sel = i == selRow;
            boolean over = mouseX >= rx && mouseX < rx + 88 && mouseY >= ry && mouseY < ry + ROW_H;
            int tv = (sel || over) ? 86 : 66;
            g.blit(WIDGETS, rx, ry, 0.0f, (float) tv, 44, ROW_H, 256, 256);
            g.blit(WIDGETS, rx + 44, ry, 156.0f, (float) tv, 44, ROW_H, 256, 256);
            // As in Minecraft's rows: what you pay on the left, the arrow,
            // what you get on the right. (Pairing his sell list with his buy
            // list gave W -> W: a merchant buys and sells the same goods.
            // Owner photo 2026-11-16_22.24.06.)
            letter(g, rx + 5, ry + 1, payWord(i));
            g.blit(VILLAGER, x + 60, ry + 4, 15.0f, 171.0f, 10, 9, 512, 256);
            letter(g, rx + 68, ry + 1, view.sell.get(i));
        }

        // The scroller, as Minecraft draws it when there is nothing to scroll.
        g.blit(VILLAGER, x + 94, greyY + 18, 6.0f, 199.0f, 6, 27, 512, 256);

        // The picked trade in the middle: the price in the slot to pay with,
        // what he gives in the slot to take from (their frames are in the
        // picture), with the price and the good's name under them.
        String selSell = selRow < view.sell.size() ? view.sell.get(selRow) : "";
        String selBuy = selRow < view.buy.size() ? view.buy.get(selRow) : "";
        String selPrice = selRow < view.price.size() ? view.price.get(selRow) : "";
        letter(g, x + 136, greyY + 37, payWord(selRow));
        letter(g, x + 220, greyY + 37, selSell);
        String payCap = !selPrice.isEmpty() ? selPrice : selBuy;
        if (!payCap.isEmpty()) {
            String t = fit(payCap, 62);
            g.drawString(font, t, x + 149 - font.width(t) / 2, greyY + 58, LABEL, false);
        }
        if (!selSell.isEmpty()) {
            String t = fit(selSell, 76);
            g.drawString(font, t, x + 228 - font.width(t) / 2, greyY + 58, LABEL, false);
        }

        // The player's inventory, drawn into the picture's slots: three rows
        // and the hotbar, with the things themselves.
        if (minecraft != null && minecraft.player != null) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    ItemStack st = minecraft.player.getInventory().getItem(9 + r * 9 + c);
                    if (!st.isEmpty()) {
                        g.renderItem(st, x + 108 + c * 18, greyY + 84 + r * 18);
                        g.renderItemDecorations(font, st, x + 108 + c * 18, greyY + 84 + r * 18);
                    }
                }
            }
            for (int c = 0; c < 9; c++) {
                ItemStack st = minecraft.player.getInventory().getItem(c);
                if (!st.isEmpty()) {
                    g.renderItem(st, x + 108 + c * 18, greyY + 142);
                    g.renderItemDecorations(font, st, x + 108 + c * 18, greyY + 142);
                }
            }
        }

        // The status box: what the trade gives and takes, and the pocket.
        g.fill(x, statusY, x + W, statusY + statusH, DARK);
        outline(g, x, statusY, x + W, statusY + statusH, 0xFF000000);
        int sy = statusY + 4;
        for (String line : ScreenText.wrap(view.status, W - 16, font::width)) {
            g.drawString(font, line, x + 5, sy, STATUS, false);
            sy += 10;
        }
        for (String line : ScreenText.wrap(view.pocket, W - 16, font::width)) {
            g.drawString(font, line, x + 5, sy, DIM, false);
            sy += 10;
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Over a trade: its full name, its price, and what he takes.
        for (int i = 0; i < view.sell.size() && i < ROWS; i++) {
            int ry = rowsY + i * ROW_H;
            if (mouseX >= x + 5 && mouseX < x + 5 + 88 && mouseY >= ry && mouseY < ry + ROW_H
                    && view.sell.get(i) != null && !view.sell.get(i).isEmpty()) {
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
                lines.add(Component.literal(capw(view.sell.get(i))).withStyle(net.minecraft.ChatFormatting.YELLOW).getVisualOrderText());
                String p = i < view.price.size() ? view.price.get(i) : "";
                if (!p.isEmpty()) {
                    lines.add(Component.literal("Price: " + p).withStyle(net.minecraft.ChatFormatting.GOLD).getVisualOrderText());
                }
                String b = i < view.buy.size() ? view.buy.get(i) : "";
                if (!b.isEmpty()) {
                    lines.add(Component.literal("He takes: " + b).withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
                }
                g.renderTooltip(font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < ROWS; i++) {
                int ry = rowsY + i * ROW_H;
                if (mx >= x + 5 && mx < x + 5 + 88 && my >= ry && my < ry + ROW_H) {
                    selRow = i;
                    said = view.rowLine(i);
                    return true;
                }
            }
            if (mx >= backX && mx < backX + backW && my >= backY && my < backY + 10) {
                back();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Back to the talk: the sheet's closeTrade — the talk goes on where it was. */
    private void back() {
        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "panelclose", "", ""));
        minecraft.setScreen(null);
    }

    /**
     * What the player pays for trade {@code i}, as one word for its slot: the
     * coin of its price ("four annas" pays in annas), or what he takes where
     * a trade has no price.
     */
    private String payWord(int i) {
        String p = i < view.price.size() ? view.price.get(i) : "";
        if (p != null && !p.isBlank()) {
            String[] words = p.strip().split("\\s+");
            return words[words.length - 1];
        }
        return i < view.buy.size() ? view.buy.get(i) : "";
    }

    /** A good's first letter, where Minecraft's screen carries its picture. */
    private void letter(GuiGraphics g, int sx, int sy, String good) {
        if (good != null && !good.isEmpty()) {
            String letter = good.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            g.drawString(font, letter, sx + (16 - font.width(letter)) / 2, sy + 4, INK, false);
        }
    }

    private void centered(GuiGraphics g, String s, int cx, int y, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private String fit(String s, int w) {
        if (s == null || font.width(s) <= w) {
            return s == null ? "" : s;
        }
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end) + "…") > w) {
            end--;
        }
        return end <= 0 ? "…" : s.substring(0, end).stripTrailing() + "…";
    }

    private static String capw(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void outline(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
        g.fill(ax, ay, bx, ay + 1, color);
        g.fill(ax, by - 1, bx, by, color);
        g.fill(ax, ay, ax + 1, by, color);
        g.fill(bx - 1, ay, bx, by, color);
    }
}
