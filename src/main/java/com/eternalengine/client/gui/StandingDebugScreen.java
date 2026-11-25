package com.eternalengine.client.gui;

import com.eternalengine.knowledge.TalkView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The sheet's debug panel: the city's standing for the player, and nothing but it. Two meters, each on
 * −100 … +100 with the whole scale visible and a needle at the value; the tier the street reads, the day,
 * the meetings, the bond; and the thread of the talk. For now, the values are shown only here — not on
 * the box, not on the card.
 */
@OnlyIn(Dist.CLIENT)
public class StandingDebugScreen extends Screen {

    private static final int BG = 0xF2101018;
    private static final int LINE = 0xFF3A5A6A;
    private static final int TITLE = 0xFFB0D0E0;
    private static final int TEXT = 0xFF9AA8B4;
    private static final int GOOD = 0xFF5D8A5D;
    private static final int BAD = 0xFF8A5D5D;

    private final TalkView view;
    private final Screen back;

    public StandingDebugScreen(TalkView view, Screen back) {
        super(Component.literal("debug — the player's standing"));
        this.view = view;
        this.back = back;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int pw = 250, ph = 118;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(px, py, px + pw, py + ph, BG);
        outline(g, px, py, px + pw, py + ph, LINE);

        g.drawString(font, "debug — the player's standing", px + 8, py + 8, TITLE, false);

        meter(g, px + 8, py + 26, "good", view.posRep, GOOD);
        meter(g, px + 8, py + 48, "bad", view.negRep, BAD);

        g.drawString(font, "tier: " + view.tier + "  ·  day: " + view.dateLabel + "  ·  meetings: " + view.meetings
                + "  ·  bond: " + view.debugBond, px + 8, py + 72, TEXT, false);
        g.drawString(font, "you: " + (view.debugYou.isEmpty() ? "the player" : view.debugYou)
                + "  ·  lastQ: " + (view.debugLastQ.isEmpty() ? "—" : view.debugLastQ)
                + "  ·  news: " + view.debugNews
                + "  ·  askRun: " + view.debugAskRun, px + 8, py + 86, TEXT, false);
        g.drawString(font, "for now: values shown only here", px + 8, py + 102, 0xFF6A7A88, false);
        g.drawString(font, "‹ back", px + pw - 44, py + ph - 14, 0xFF7A8A98, false);
    }

    /** One meter: the name, the bar with the −100 / 0 / +100 scale, the needle, and the value. */
    private void meter(GuiGraphics g, int x, int y, String name, int value, int color) {
        g.drawString(font, name, x, y + 2, TEXT, false);
        int bx = x + 26, by = y + 4, bw = 150;
        g.fill(bx, by, bx + bw, by + 6, 0xFF22222E);
        // The scale: −100 at the left, 0 in the middle, +100 at the right.
        g.fill(bx, by, bx + 1, by + 6, 0xFF6A6A76);
        g.fill(bx + bw / 2, by, bx + bw / 2 + 1, by + 6, 0xFF8A8A96);
        g.fill(bx + bw - 1, by, bx + bw, by + 6, 0xFF6A6A76);
        int v = Math.max(-100, Math.min(100, value));
        int nx = bx + (bw * (v + 100)) / 200;
        g.fill(nx - 1, by - 1, nx + 1, by + 7, color);
        g.drawString(font, String.valueOf(v > 0 ? "+" + v : v), bx + bw + 10, y + 2, color, false);
        g.drawString(font, "−100", bx, by + 8, 0xFF5A6A78, false);
        g.drawString(font, "0", bx + bw / 2 - 2, by + 8, 0xFF5A6A78, false);
        g.drawString(font, "+100", bx + bw - 16, by + 8, 0xFF5A6A78, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // A click anywhere on the panel goes back to the talk.
        int pw = 250, ph = 118;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (button == 0 && mx >= px && mx < px + pw && my >= py && my < py + ph) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void outline(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
        g.fill(ax, ay, bx, ay + 1, color);
        g.fill(ax, by - 1, bx, by, color);
        g.fill(ax, ay, ax + 1, by, color);
        g.fill(bx - 1, ay, bx, by, color);
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft.getInstance().setScreen(back);
    }
}
