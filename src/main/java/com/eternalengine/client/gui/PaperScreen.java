package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.PaperPainter;
import com.eternalengine.client.gui.paint.TradePainter;
import com.eternalengine.news.PaperView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Step 19d: a newspaper (drawn by {@link PaperPainter}); the mouse wheel and the arrow keys scroll it. */
@OnlyIn(Dist.CLIENT)
public class PaperScreen extends Screen {

    private final PaperView view;
    private final PaperPainter.State state = new PaperPainter.State();
    private List<TradePainter.Hot> hots = List.of();

    public PaperScreen(PaperView view) {
        super(Component.literal(view.name));
        this.view = view;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hots = PaperPainter.paint(new GuiCanvas(g, font), view, state, width, height, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (TradePainter.Hot h : hots) {
            if (button == 0 && h.contains(mx, my) && "close".equals(h.action())) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        state.scroll = Math.max(0, Math.min(state.maxScroll, state.scroll - (int) (delta * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_UP
                || key == GLFW.GLFW_KEY_PAGE_UP) {
            int step = key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP ? height - 40 : 20;
            int dir = key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            state.scroll = Math.max(0, Math.min(state.maxScroll, state.scroll + dir * step));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
