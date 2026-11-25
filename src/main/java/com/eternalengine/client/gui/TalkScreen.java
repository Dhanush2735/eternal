package com.eternalengine.client.gui;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.knowledge.TalkView;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TalkActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The talk box from the approved preview (step 18): the villager's portrait, name and job, the date, what they
 * say with gold clickable names, numbered questions (keys 1 to 9), "Ask about" names, and Back, Other topics, a
 * box to type a name, Ask and Goodbye. The world stays visible; the game does not pause.
 *
 * <p>Step 18c: where things go is worked out by {@link TalkLayout}, which fits the box to any screen (the step-18
 * box ran off the right edge at 480 GUI units, the user's 1920 x 1080 screen with the automatic GUI scale, and
 * had no room left for the date). The mouse wheel scrolls what the villager says when a low screen cannot show
 * all of it.
 */
@OnlyIn(Dist.CLIENT)
public class TalkScreen extends Screen {

    private static final int BG = 0xE60E0E12;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int GOLD = 0xFFFFD75E;
    private static final int GOLD_HOVER = 0xFFFFF1A8;
    private static final int NAME = 0xFFFFFF55;
    private static final int GRAY = 0xFFA8A8B0;
    private static final int DIM = 0xFF9A9AA4;
    private static final int LINE_H = TalkLayout.LINE_H;
    private static final double MAX_DISTANCE_SQ = 8.0d * 8.0d;

    private TalkView view;
    private EditBox ask;
    private long waitingSince;
    private TalkLayout layout;
    private int textScroll;

    public TalkScreen(TalkView view) {
        super(Component.literal(view.npcName));
        this.view = view;
    }

    public int entityId() {
        return view.entityId;
    }

    /** Step 21: a talk box that only says the talk is over ("Lakshmi has left."), for the trade screen to switch to. */
    public static TalkScreen ended(int entityId, String npcName, String job, String dateLabel, String line) {
        return new TalkScreen(TalkView.ended(entityId, npcName, job, 1, dateLabel, line));
    }

    /** Step 21: the line for a person who has gone. */
    static String leftLine(String npcName) {
        return npcName == null || npcName.isBlank() ? "The person you were talking to has left." : npcName + " has left.";
    }

    public void update(TalkView next) {
        this.view = next;
        this.waitingSince = 0;
        this.textScroll = 0;
        if (ask != null) {
            ask.setValue("");
        }
        layout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        ask = new EditBox(this.font, 0, 0, 120, 16, Component.literal("Ask about a name"));
        ask.setMaxLength(60);
        ask.setHint(Component.literal("Ask about a name…").withStyle(s -> s.withColor(0x808080)));
        addRenderableWidget(ask);
        layout();
    }

    /** Places everything for this screen size; the box grows upward from above the hotbar to fit. */
    private void layout() {
        if (ask == null) {
            return;
        }
        layout = TalkLayout.of(width, height, font::width, view, textScroll);
        textScroll = layout.scroll;
        ask.setX(layout.askX);
        ask.setY(layout.askY);
        ask.setWidth(layout.askW);
        ask.setHeight(layout.askH);
        // Minecraft does not shorten the grey hint, so the layout picks one that fits the box.
        ask.setHint(Component.literal(layout.askHint).withStyle(s -> s.withColor(0x808080)));
        ask.visible = layout.askVisible;
        if (!layout.askVisible && getFocused() == ask) {
            setFocused(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TalkLayout l = layout;
        if (l == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        // The box.
        g.fill(l.x0, l.y0, l.x1, l.y1, BG);
        outline(g, l.x0, l.y0, l.x1, l.y1, 0xFF000000);
        outline(g, l.x0 + 1, l.y0 + 1, l.x1 - 1, l.y1 - 1, 0x29FFFFFF);

        // Portrait: the villager, looking at the mouse.
        if (l.portrait) {
            int pw = TalkLayout.PORTRAIT_W, ph = TalkLayout.PORTRAIT_H;
            int px = l.portraitX, py = l.portraitY;
            g.fill(px, py, px + pw, py + ph, 0xFF8B8B8B);
            g.fill(px, py, px + pw, py + 1, 0xFF373737);
            g.fill(px, py, px + 1, py + ph, 0xFF373737);
            g.fill(px, py + ph - 1, px + pw, py + ph, 0xFFFFFFFF);
            g.fill(px + pw - 1, py, px + pw, py + ph, 0xFFFFFFFF);
            Entity e = npc();
            if (e instanceof HumanNpcEntity human) {
                int cx = px + pw / 2, feet = py + ph - 6;
                g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, 30, (float) (cx - mouseX),
                        (float) (feet - 50 - mouseY), human);
                g.disableScissor();
            }
        }

        // Name, job, date (beside them, or on the line below when there is no room).
        if (!l.name.text().isEmpty()) {
            g.drawString(font, l.name.text(), l.name.x(), l.name.y(), NAME, true);
        }
        if (!l.job.text().isEmpty()) {
            g.drawString(font, l.job.text(), l.job.x(), l.job.y(), GRAY, false);
        }
        if (!l.date.text().isEmpty()) {
            g.drawString(font, l.date.text(), l.date.x(), l.date.y(), DIM, false);
        }

        // What the villager says; names in gold, underlined with dots.
        String hoverCard = null;
        for (TalkLayout.Word w : l.words) {
            if (w.cardId() != null && mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() - 1 && mouseY < w.y() + LINE_H - 1) {
                hoverCard = w.cardId();
            }
        }
        for (TalkLayout.Word w : l.words) {
            boolean link = w.cardId() != null;
            int color = !link ? TEXT : w.cardId().equals(hoverCard) ? GOLD_HOVER : GOLD;
            g.drawString(font, w.text(), w.x(), w.y(), color, true);
            if (link) {
                for (int dx = 0; dx < w.w(); dx += 2) {
                    g.fill(w.x() + dx, w.y() + 9, w.x() + dx + 1, w.y() + 10, color);
                }
            }
        }
        // Scroll arrows when not everything fits (mouse wheel).
        if (l.moreAbove) {
            arrow(g, l.arrowX, l.arrowUpY, true, DIM);
        }
        if (l.moreBelow) {
            arrow(g, l.arrowX, l.arrowDownY, false, DIM);
        }
        for (TalkLayout.Text h : l.hint) {
            g.drawString(font, h.text(), h.x(), h.y(), DIM, false);
        }

        // Options, names, controls.
        boolean waiting = waitingSince > 0 && System.currentTimeMillis() - waitingSince < 3000;
        if (l.askAbout != null) {
            g.drawString(font, l.askAbout.text(), l.askAbout.x(), l.askAbout.y(), DIM, false);
        }
        for (TalkLayout.Box b : l.boxes) {
            boolean over = b.over(mouseX, mouseY) && !waiting;
            int ty = b.y() + (b.h() - 8) / 2;
            switch (b.kind()) {
                case OPTION -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), waiting ? 0xFF5A5A5A : 0xFF6F6F6F);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF000000);
                    if (over) {
                        outline(g, b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, 0xFFFFFFFF);
                    } else {
                        g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + 2, 0xFFA0A0A0);
                        g.fill(b.x() + 1, b.y() + b.h() - 2, b.x() + b.w() - 1, b.y() + b.h() - 1, 0xFF464646);
                    }
                    int tx = b.x() + 5;
                    g.drawString(font, b.number(), tx, ty, 0xFFC8C8C8, true);
                    tx += font.width(b.number()) + 4;
                    g.drawString(font, b.label(), tx, ty, waiting ? 0xFFA0A0A0 : over ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                }
                case CHIP -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0x2EFFD75E : 0x12FFD75E);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF6B5A2A);
                    g.drawString(font, b.label(), b.x() + 5, b.y() + 2, over ? GOLD_HOVER : GOLD, false);
                }
                case BACK, TOPICS, ASK, BYE -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0xFF7F7F7F : 0xFF5F5F5F);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF000000);
                    g.drawString(font, b.label(), b.x() + 6, ty, over ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                }
                case CLOSE -> {
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0xFFAAAAAA : 0xFF555555);
                    g.drawString(font, b.label(), b.x() + 3, b.y() + 2, over ? 0xFFFFFFFF : 0xFFDDDDDD, false);
                }
            }
        }
        // The line above the controls.
        if (l.ruleY >= 0) {
            g.fill(l.bodyX, l.ruleY, l.bodyX + l.bodyW, l.ruleY + 1, 0x1FFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** A small triangle, 5 wide and 3 high, pointing up or down. */
    private static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? y + i : y + 2 - i;
            g.fill(x + 2 - i, row, x + 3 + i, row + 1, color);
        }
    }

    private static void outline(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
        g.fill(ax, ay, bx, ay + 1, color);
        g.fill(ax, by - 1, bx, by, color);
        g.fill(ax, ay, ax + 1, by, color);
        g.fill(bx - 1, ay, bx, by, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        TalkLayout l = layout;
        if (button == 0 && l != null) {
            for (TalkLayout.Word w : l.words) {
                if (w.cardId() != null && mx >= w.x() && mx < w.x() + w.w() && my >= w.y() - 1 && my < w.y() + LINE_H - 1) {
                    send("card", w.cardId(), "");
                    return true;
                }
            }
            for (TalkLayout.Box b : l.boxes) {
                if (b.over(mx, my)) {
                    click(b);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(TalkLayout.Box b) {
        switch (b.kind()) {
            case OPTION -> {
                if (b.index() >= 0 && b.index() < view.options.size()) {
                    TalkView.Option o = view.options.get(b.index());
                    send(o.action(), o.a(), o.b());
                }
            }
            case CHIP -> {
                if (b.index() >= 0 && b.index() < view.names.size()) {
                    send("card", view.names.get(b.index()).cardId(), "");
                }
            }
            case BACK -> send("back", "", "");
            case TOPICS -> send("topics", "", "");
            case ASK -> askTyped();
            case BYE, CLOSE -> onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        TalkLayout l = layout;
        if (l != null && l.scrollMax > 0 && mx >= l.x0 && mx < l.x1 && my >= l.y0 && my < l.y1 && delta != 0) {
            int next = Math.max(0, Math.min(l.scrollMax, textScroll - (int) Math.signum(delta)));
            if (next != textScroll) {
                textScroll = next;
                layout();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ask != null && ask.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                askTyped();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return ask.keyPressed(key, scan, mods) || ask.canConsumeInput() || super.keyPressed(key, scan, mods);
            }
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int i = key - GLFW.GLFW_KEY_1;
            if (i < view.options.size()) {
                TalkView.Option o = view.options.get(i);
                send(o.action(), o.a(), o.b());
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && view.canBack) {
            send("back", "", "");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (ask != null) {
            ask.tick();
        }
        if (view.ended) {
            return; // step 21: the closing line stays until the player closes the box
        }
        Entity e = npc();
        Minecraft mc = Minecraft.getInstance();
        if (e == null || !e.isAlive()) {
            // Step 21: never silently. The person has gone: the box says so and offers Goodbye; the server lets go.
            EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "bye", "", ""));
            update(TalkView.ended(view.entityId, view.npcName, view.job, view.level, view.dateLabel, leftLine(view.npcName)));
            return;
        }
        if (mc.player == null || e.distanceToSqr(mc.player) > MAX_DISTANCE_SQ) {
            if (mc.player != null && !view.npcName.isBlank()) {
                mc.player.displayClientMessage(Component.literal("You walked away from " + view.npcName + "."), true);
            }
            onClose();
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        String typed = ask == null ? "" : ask.getValue();
        super.resize(mc, w, h);
        if (ask != null) {
            ask.setValue(typed);
        }
    }

    @Override
    public void onClose() {
        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "bye", "", ""));
        super.onClose();
    }

    private void askTyped() {
        String typed = ask == null ? "" : ask.getValue().trim();
        if (!typed.isEmpty()) {
            send("free", typed, "");
        }
    }

    private void send(String action, String a, String b) {
        if (view.ended) {
            return; // step 21: nobody to ask
        }
        if (waitingSince > 0 && System.currentTimeMillis() - waitingSince < 3000) {
            return;
        }
        waitingSince = System.currentTimeMillis();
        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, action, a, b));
    }

    private Entity npc() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(view.entityId);
    }
}
