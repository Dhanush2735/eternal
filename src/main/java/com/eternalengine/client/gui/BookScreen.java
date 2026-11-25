package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.BookPainter;
import com.eternalengine.client.gui.paint.TradePainter;
import com.eternalengine.knowledge.BookView;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.BookActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Step 20: the history book (drawn by {@link BookPainter}). Typing finds a name; a click opens a name, a tab, or tracks
 * a place on the compass (the server answers with the next page); the mouse wheel scrolls the page it is over.
 */
@OnlyIn(Dist.CLIENT)
public class BookScreen extends Screen {

    private static final int MAX_FILTER = 40;
    private BookView view;
    private final BookPainter.State state = new BookPainter.State();
    private List<TradePainter.Hot> hots = List.of();

    public BookScreen(BookView view) {
        super(Component.literal("History book"));
        this.view = view;
    }

    /** A new page from the server: the search words and the list's place stay. */
    public void update(BookView next) {
        if (!next.card.equals(view.card) || !next.tab.equals(view.tab)) {
            state.rightScroll = 0;
        }
        view = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hots = BookPainter.paint(new GuiCanvas(g, font), view, state, width, height, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        for (TradePainter.Hot h : hots) {
            if (!h.contains(mx, my)) {
                continue;
            }
            switch (h.action()) {
                case "close" -> onClose();
                case "search" -> state.focused = true;
                case "card" -> send("card", h.arg(), "notes");
                case "tab" -> send("tab", view.card, h.arg());
                case "track" -> send("track", h.arg(), view.tab);
                case "untrack" -> send("untrack", h.arg(), view.tab);
                default -> {
                    continue;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(String action, String card, String tab) {
        EternalEnginePacketHandler.sendToServer(new BookActionPacket(action, card, tab));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) (delta * 20);
        if (mx < state.splitX) {
            state.leftScroll = Math.max(0, Math.min(state.maxLeft, state.leftScroll - step));
        } else {
            state.rightScroll = Math.max(0, Math.min(state.maxRight, state.rightScroll - step));
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (state.focused && ch >= ' ' && ch != 127 && state.filter.length() < MAX_FILTER) {
            state.filter += ch;
            state.leftScroll = 0;
            return true;
        }
        return super.charTyped(ch, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && !state.filter.isEmpty()) {
            state.filter = state.filter.substring(0, state.filter.length() - 1);
            state.leftScroll = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_UP
                || key == GLFW.GLFW_KEY_PAGE_UP) {
            int step = key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP ? height - 60 : 20;
            int dir = key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            state.rightScroll = Math.max(0, Math.min(state.maxRight, state.rightScroll + dir * step));
            return true;
        }
        // Letters must not open the inventory or chat while typing a name: only Escape closes.
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scan, mods);
        }
        return state.focused || super.keyPressed(key, scan, mods);
    }
}
