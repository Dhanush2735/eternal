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
 * The talk box from the approved interaction sheet: the person's face, his name with the job's sign in
 * the job line, the date and the close; the one big action button for his trade (the sheet's action
 * slot); the roles band with his standing in the street; what he says, gold names clickable; the names
 * heard as chips; the numbered questions (a bridge until the free engine lands); the line of what to
 * say; and the field to say it in, with Say. The control row holds ‹ Back through
 * the talk (Backspace does the same), Ask where…, Who is he? and Goodbye. The
 * world stays visible; the game does not pause.
 *
 * <p>Where things go is worked out by {@link TalkBoxLayout}. The old {@link TalkScreen} stands untouched
 * beside this one; this box is the one that opens from a person now.
 */
@OnlyIn(Dist.CLIENT)
public class TalkBoxScreen extends Screen {

    private static final int BG = 0xE60E0E12;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int GOLD = 0xFFFFD75E;
    private static final int GOLD_HOVER = 0xFFFFF1A8;
    private static final int NAME = 0xFFFFFF55;
    private static final int GRAY = 0xFFA8A8B0;
    private static final int DIM = 0xFF9A9AA4;
    private static final int LINE_H = TalkBoxLayout.LINE_H;
    private static final int ACTION_BG = 0xFF6A5A2E;
    private static final int ACTION_BG_HOVER = 0xFF7D6A38;
    private static final int ACTION_LINE = 0xFF8A7434;
    private static final int ACTION_LINE_HOVER = 0xFFA88C44;
    private static final double MAX_DISTANCE_SQ = 8.0d * 8.0d;

    private TalkView view;
    private EditBox say;
    private long waitingSince;
    private TalkBoxLayout layout;
    private int textScroll;

    public TalkBoxScreen(TalkView view) {
        super(Component.literal(view.npcName));
        this.view = view;
    }

    public int entityId() {
        return view.entityId;
    }

    public void update(TalkView next) {
        this.view = next;
        this.waitingSince = 0;
        this.textScroll = 0;
        if (say != null) {
            say.setValue("");
        }
        applyRoleMark(); // every fresh reply comes back without the sign; put it back
        layout();
        if (!view.ended) {
            setFocused(say); // the sheet's field is always ready: every reply hands the words back
        }
    }

    /** The job's sign sits in the job line, dimmed, so the trade reads at a glance. */
    private void applyRoleMark() {
        Entity e = npc();
        if (e instanceof HumanNpcEntity human) {
            String mark = HumanNpcEntity.roleMark(human.getRole());
            if (!mark.isEmpty()) {
                if (!view.job.contains("\u00a7")) {
                    view.job = (view.job.isEmpty() ? "" : view.job + " ") + "\u00a77" + mark;
                }
                if (view.actionLabel != null && !view.actionLabel.isEmpty() && !view.actionLabel.contains("\u00a7")) {
                    view.actionLabel = "\u00a77" + mark + " " + view.actionLabel;
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        applyRoleMark(); // the job's sign in the job line, dimmed, so the trade reads at a glance
        say = new EditBox(this.font, 0, 0, 120, 16, Component.literal("Say something"));
        say.setMaxLength(120);
        addRenderableWidget(say);
        layout();
        // The sheet's field is always ready: the words are the way in, so the field is focused on arrival.
        if (!view.ended && say != null) {
            setFocused(say);
        }
    }

    private void layout() {
        if (say == null) {
            return;
        }
        layout = TalkBoxLayout.of(width, height, font::width, view, textScroll);
        textScroll = layout.scroll;
        say.setX(layout.askX);
        say.setY(layout.askY);
        say.setWidth(layout.askW);
        say.setHeight(layout.askH);
        say.setHint(Component.literal(layout.askHint).withStyle(s -> s.withColor(0x808080)));
        say.visible = layout.askVisible;
        if (!layout.askVisible && getFocused() == say) {
            setFocused(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TalkBoxLayout l = layout;
        if (l == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        // The box.
        g.fill(l.x0, l.y0, l.x1, l.y1, BG);
        outline(g, l.x0, l.y0, l.x1, l.y1, 0xFF000000);
        outline(g, l.x0 + 1, l.y0 + 1, l.x1 - 1, l.y1 - 1, 0x29FFFFFF);

        // Portrait: the person, looking at the mouse.
        if (l.portrait) {
            int pw = TalkBoxLayout.PORTRAIT_W, ph = TalkBoxLayout.PORTRAIT_H;
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

        // Name, job (with the trade's sign), date.
        if (!l.name.text().isEmpty()) {
            g.drawString(font, l.name.text(), l.name.x(), l.name.y(), NAME, true);
        }
        if (!l.job.text().isEmpty()) {
            g.drawString(font, l.job.text(), l.job.x(), l.job.y(), GRAY, false);
        }
        if (!l.date.text().isEmpty()) {
            g.drawString(font, l.date.text(), l.date.x(), l.date.y(), DIM, false);
        }

        // The one big button for his trade.
        if (l.action != null) {
            boolean over = l.action.over(mouseX, mouseY);
            g.fill(l.action.x(), l.action.y(), l.action.x() + l.action.w(), l.action.y() + l.action.h(),
                    over ? ACTION_BG_HOVER : ACTION_BG);
            outline(g, l.action.x(), l.action.y(), l.action.x() + l.action.w(), l.action.y() + l.action.h(),
                    over ? ACTION_LINE_HOVER : ACTION_LINE);
            g.drawString(font, l.action.label(), l.action.x() + 6, l.action.y() + (l.action.h() - 8) / 2,
                    over ? 0xFFFFF3D0 : 0xFFFFE9B0, true);
        }

        // The prototype's dim goods line, without the old role band.
        if (l.role != null) {
            g.drawString(font, l.role.label(), l.role.x(), l.role.y(), 0xFF7D828D, false);
        }

        // The mood line: the dot, and how the person is toward the player.
        if (l.mood != null && !l.mood.text().isEmpty()) {
            int dot = (view.moodDot & 0xFFFFFF) | 0xFF000000;
            g.fill(l.mood.x() - 6, l.mood.y() + 3, l.mood.x() - 3, l.mood.y() + 6, dot);
            g.drawString(font, l.mood.text(), l.mood.x(), l.mood.y(), 0xFF9AA0AB, false);
        }

        // What he says; names in gold, underlined with dots.
        String hoverCard = null;
        for (TalkBoxLayout.Word w : l.words) {
            if (w.cardId() != null && mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() - 1 && mouseY < w.y() + LINE_H - 1) {
                hoverCard = w.cardId();
            }
        }
        // While the "Ask where" menu is open, the words under it are not drawn
        // at all. (Flushing first was not enough: in the game the spoken text
        // still painted over the menu's highlighted row — owner photo
        // 2026-11-16_22.23.04. Words that are never drawn cannot show through.)
        int menuX = 0, menuY = 0, menuR = -1, menuB = -1;
        if (findOpen && l.findBtn != null) {
            menuX = l.findBtn.x();
            menuY = findTop(l.findBtn);
            menuR = menuX + findWidth(l.findBtn);
            menuB = menuY + (findRows(l.findBtn) + 1) * FIND_ROW_H;
        }
        for (TalkBoxLayout.Word w : l.words) {
            if (menuR >= 0 && w.x() < menuR && w.x() + w.w() > menuX && w.y() - 1 < menuB && w.y() + LINE_H - 1 > menuY) {
                continue;
            }
            boolean link = w.cardId() != null;
            int color = !link ? 0xFFB9B9C4 : w.cardId().equals(hoverCard) ? GOLD_HOVER : GOLD;
            g.drawString(font, w.text(), w.x(), w.y(), color, true);
            if (link) {
                for (int dx = 0; dx < w.w(); dx += 2) {
                    g.fill(w.x() + dx, w.y() + 9, w.x() + dx + 1, w.y() + 10, color);
                }
            }
        }
        if (l.moreAbove) {
            arrow(g, l.arrowX, l.arrowUpY, true, DIM);
        }
        if (l.moreBelow) {
            arrow(g, l.arrowX, l.arrowDownY, false, DIM);
        }
        for (TalkBoxLayout.Text h : l.hint) {
            g.drawString(font, h.text(), h.x(), h.y(), DIM, false);
        }

        // Names, questions, the field's line, controls.
        if (l.askAbout != null) {
            g.drawString(font, l.askAbout.text(), l.askAbout.x(), l.askAbout.y(), DIM, false);
        }
        // The prototype's grey-blue, personal blue and green parting chips.
        for (TalkBoxLayout.Box b : l.chips) {
            boolean over = b.over(mouseX, mouseY);
            String act = b.index() >= 0 && b.index() < view.chips.size() ? view.chips.get(b.index()).action() : "";
            int face = switch (act) {
                case "bye" -> 0xFF566B56;
                case "howtrade", "family" -> 0xFF44506A;
                default -> 0xFF4A5263;
            };
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), face);
            outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0xFFFFFFFF : 0xFF000000);
            g.drawString(font, b.label(), b.x() + 5, b.y() + (b.h() - 8) / 2, 0xFFE8E6DF, false);
        }
        for (TalkBoxLayout.Box b : l.boxes) {
            boolean over = b.over(mouseX, mouseY);
            int ty = b.y() + (b.h() - 8) / 2;
            switch (b.kind()) {
                case OTHER -> {
                    // Index 4 is only the portrait's hit area, not a painted control.
                    if (b.index() == 4) break;
                    // Index 5 is ‹ Back: dim until the talk has a step to go back to.
                    boolean backOff = b.index() == 5 && !view.canBack;
                    int bc = switch (b.index()) {
                        case 2 -> 0xFF4D4460;
                        case 3 -> 0xFF566B56;
                        case 5 -> backOff ? 0xFF33373E : 0xFF4A5263;
                        default -> 0xFF445A6B;
                    };
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bc);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over && !backOff ? 0xFFFFFFFF : 0xFF000000);
                    g.drawString(font, b.label(), b.x() + 6, ty, backOff ? 0xFF8A8F98 : 0xFFFFFFFF, false);
                }
                case OPTION -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF6F6F6F);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF000000);
                    if (over) {
                        outline(g, b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, 0xFFFFFFFF);
                    }
                    int tx = b.x() + 5;
                    g.drawString(font, b.number(), tx, ty, 0xFFC8C8C8, true);
                    tx += font.width(b.number()) + 4;
                    g.drawString(font, b.label(), tx, ty, over ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                }
                case CHIP -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0x2EFFD75E : 0x12FFD75E);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF6B5A2A);
                    g.drawString(font, b.label(), b.x() + 5, b.y() + 2, over ? GOLD_HOVER : GOLD, false);
                }
                case SAY -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0xFF7F7F7F : 0xFF5F5F5F);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF000000);
                    g.drawString(font, b.label(), b.x() + 6, ty, over ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                }
                case CLOSE -> {
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0xFFAAAAAA : 0xFF555555);
                    g.drawString(font, b.label(), b.x() + 3, b.y() + 2, over ? 0xFFFFFFFF : 0xFFDDDDDD, false);
                }
                case DEBUG -> {
                    g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), over ? 0x2A5A7A8A : 0x144A5A6A);
                    outline(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF3A5A6A);
                    g.drawString(font, b.label(), b.x() + 5, b.y() + 2, over ? 0xFFB0D0E0 : 0xFF8090A0, false);
                }
                case ACTION, ROLE -> { // drawn above, where they have their own style
                }
            }
        }
        if (l.sayhint != null && !l.sayhint.text().isEmpty()) {
            g.drawString(font, l.sayhint.text(), l.sayhint.x(), l.sayhint.y(), DIM, false);
        }
        if (l.ruleY >= 0) {
            g.fill(l.bodyX, l.ruleY, l.bodyX + l.bodyW, l.ruleY + 1, 0x1FFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (findOpen && l.findBtn != null) {
            // Minecraft queues all text and paints it at the end of the frame, so
            // text drawn above would land on top of the menu's background even
            // though the menu is drawn last. Flush the text queue first, so the
            // menu's fill covers the words underneath it.
            g.flush();
            drawFindMenu(g, l.findBtn, mouseX, mouseY);
            g.flush();
        }
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
        TalkBoxLayout l = layout;
        if (button == 0 && l != null) {
            if (findOpen && l.findBtn != null) {
                int top = findTop(l.findBtn), rows = findRows(l.findBtn);
                int w = findWidth(l.findBtn);
                int backY = top + rows * FIND_ROW_H;
                if (mx >= l.findBtn.x() && mx < l.findBtn.x() + w
                        && my >= backY + 1 && my < backY + FIND_ROW_H) {
                    findOpen = false; // the back row
                } else if (mx >= l.findBtn.x() && mx < l.findBtn.x() + w
                        && my >= top && my < top + rows * FIND_ROW_H) {
                    chooseFind(findScroll + (int)((my - top) / FIND_ROW_H));
                } else findOpen = false;
                return true;
            }
            for (TalkBoxLayout.Word w : l.words) {
                if (w.cardId() != null && mx >= w.x() && mx < w.x() + w.w() && my >= w.y() - 1 && my < w.y() + LINE_H - 1) {
                    send("card", w.cardId(), "");
                    return true;
                }
            }
            if (l.action != null && l.action.over(mx, my)) {
                clickAction();
                return true;
            }
            for (TalkBoxLayout.Box b : l.chips) {
                if (b.over(mx, my)) {
                    clickChip(b);
                    return true;
                }
            }
            for (TalkBoxLayout.Box b : l.boxes) {
                if (b.over(mx, my)) {
                    click(b);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /**
     * The person's trade (the sheet's red box): the container trades open Minecraft's trade screen,
     * the counter roles open their counter as the lane describes it (the era's goods and prices, or
     * the honest line), and a role without a counter puts the cursor in the field. The server says
     * which is which, in the view's action kind.
     */
    private void clickAction() {
        if (view == null) {
            setFocused(say);
            return;
        }
        if ("container".equals(view.actionKind)) {
            send("trade", "", "");
        } else if ("counter".equals(view.actionKind)) {
            send("counter", "", "");
        } else {
            setFocused(say);
        }
    }

    private void click(TalkBoxLayout.Box b) {
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
            case OTHER -> clickOther(b);
            case SAY -> sayTyped();
            case CLOSE -> onClose();
            case DEBUG -> { /* No debug screen in NPC interaction. */ }
            case ACTION, ROLE -> {
            }
        }
    }

    /**
     * The sheet's chip row: the chips ask the questions the sheet asks (the event, the ruler,
     * the day's sight, how the trade is, the lane's trades, the family); "swap" opens the
     * sheet's trade screen like the red box; "bye" is the sheet's parting line.
     */
    private void clickChip(TalkBoxLayout.Box b) {
        if (b.index() < 0 || b.index() >= view.chips.size()) {
            return;
        }
        TalkView.Chip chip = view.chips.get(b.index());
        switch (chip.action()) {
            case "swap" -> send("trade", "", "");
            case "bye" -> send("goodbye", "", "");
            default -> send("chip", chip.action(), "");
        }
    }

    /** The control row and the portrait: Back, the "Ask where" pick, his page (the register), Goodbye. */
    private void clickOther(TalkBoxLayout.Box b) {
        switch (b.index()) {
            case 1 -> { findOpen = true; findSelected = 0; findScroll = 0; }
            case 2, 4 -> send("register", "", "");
            case 3 -> send("goodbye", "", "");
            case 5 -> { if (view.canBack) send("back", "", ""); }
            default -> {
            }
        }
    }

    /** Complete role list from the approved sheet, in the same order. */
    private static final String[][] FIND_ROLES = {
            {"citizen", "Townsperson", ""},
            {"farmer", "Farmer", "♣"},
            {"forager", "Forager", "⚘"},
            {"artisan", "Artisan", "⚙"},
            {"merchant", "Merchant", "⚖"},
            {"trader", "Trader", "⇄"},
            {"pedlar", "Pedlar", "➤"},
            {"money_changer", "Money Changer", "⇌"},
            {"paper_seller", "Paper Seller", "¶"},
            {"news_writer", "News Writer", "✍"},
            {"town_crier", "Town Crier", "◉"},
            {"weaver", "Weaver", "▦"},
            {"smith", "Smith", "⚒"},
            {"mason", "Mason", "▨"},
            {"scribe", "Scribe", "✎"},
            {"scholar", "Scholar", "✒"},
            {"clerk", "Clerk", "✉"},
            {"teacher", "Teacher", "★"},
            {"priest", "Priest", "✦"},
            {"doctor", "Doctor", "✚"},
            {"healer", "Healer", "✚"},
            {"quarantine_inspector", "Inspector", "✚"},
            {"soldier", "Soldier", "⚔"},
            {"guard", "Guard", "♟"},
            {"royal_guard", "Royal Guard", "♟"},
            {"commander", "Commander", "♞"},
            {"ruler", "Ruler", "♔"},
            {"sovereign_ruler", "Sovereign", "♚"},
            {"court_chancellor", "Chancellor", "♛"},
            {"caravan_master", "Caravan Master", "⛺"},
            {"caravan_guard", "Caravan Guard", "♟"},
            {"ship_captain", "Ship Captain", "⚓"},
            {"ship_marine", "Ship Marine", "⚔"},
            {"imperial_mason", "Imperial Mason", "▨"},
            {"bandit", "Bandit", "⚡"},
            {"rival_infiltrator", "The Unseen One", "◌"},
    };
    private static final int FIND_ROW_H = 14;
    private boolean findOpen;
    private int findScroll, findSelected;
    private int findRows(TalkBoxLayout.Box b) {
        // One row is kept for the back row under the roles.
        int space = Math.max(b.y(), height - b.y() - b.h());
        return Math.max(1, Math.min(10, Math.max(0, space - FIND_ROW_H) / FIND_ROW_H));
    }
    private int findTop(TalkBoxLayout.Box b) {
        int h = (findRows(b) + 1) * FIND_ROW_H;
        return b.y() >= h ? b.y() - h : Math.max(0, Math.min(height - h, b.y() + b.h()));
    }
    private int findWidth(TalkBoxLayout.Box b) {
        int w = b.w();
        for (String[] r : FIND_ROLES) w = Math.max(w, font.width(r[2] + " " + r[1]) + 12);
        return Math.max(1, Math.min(w, width - b.x()));
    }
    private void drawFindMenu(GuiGraphics g, TalkBoxLayout.Box b, int mx, int my) {
        int top = findTop(b), rows = findRows(b), w = findWidth(b);
        int totalH = (rows + 1) * FIND_ROW_H;
        g.fill(b.x(), top, b.x() + w, top + totalH, 0xFF1C1C1C);
        outline(g, b.x(), top, b.x() + w, top + totalH, 0xFF9EA6B2);
        g.enableScissor(b.x(), top, b.x() + w, top + rows * FIND_ROW_H);
        for (int i = 0; i < rows && findScroll + i < FIND_ROLES.length; i++) {
            int n = findScroll + i, y = top + i * FIND_ROW_H;
            boolean over = mx >= b.x() && mx < b.x() + w && my >= y && my < y + FIND_ROW_H;
            if (over || n == findSelected) g.fill(b.x()+1, y, b.x()+w-1, y+FIND_ROW_H, 0xFF53687C);
            String[] r = FIND_ROLES[n];
            g.drawString(font, (r[2].isEmpty() ? "" : r[2] + " ") + r[1], b.x()+4, y+3, 0xFFF4F4F4, false);
        }
        g.disableScissor();
        // The back row, under the roles: closes the list and returns to the talk.
        int backY = top + rows * FIND_ROW_H;
        g.fill(b.x(), backY, b.x() + w, backY + 1, 0xFF9EA6B2);
        boolean overBack = mx >= b.x() && mx < b.x() + w && my >= backY + 1 && my < backY + FIND_ROW_H;
        if (overBack) g.fill(b.x()+1, backY+1, b.x()+w-1, backY+FIND_ROW_H, 0xFF53687C);
        g.drawString(font, "‹ Back", b.x()+4, backY+4, overBack ? 0xFFFFFFFF : 0xFFB9C4D0, false);
    }
    private void chooseFind(int n) {
        if (n < 0 || n >= FIND_ROLES.length) return;
        findOpen = false;
        String name = FIND_ROLES[n][1].toLowerCase(java.util.Locale.ROOT).replaceFirst("^the ", "");
        send("free", "where is the " + name, "");
        setFocused(say);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        TalkBoxLayout l = layout;
        if (findOpen && l != null && l.findBtn != null && delta != 0) {
            int rows = findRows(l.findBtn);
            findScroll = Math.max(0, Math.min(FIND_ROLES.length - rows, findScroll - (int)Math.signum(delta)));
            findSelected = Math.max(findScroll, Math.min(findScroll + rows - 1, findSelected));
            return true;
        }
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
        if (findOpen && layout != null && layout.findBtn != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { findOpen = false; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { chooseFind(findSelected); return true; }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                findSelected = Math.max(0, Math.min(FIND_ROLES.length - 1, findSelected + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
                int rows = findRows(layout.findBtn);
                if (findSelected < findScroll) findScroll = findSelected;
                if (findSelected >= findScroll + rows) findScroll = findSelected - rows + 1;
                return true;
            }
            return true;
        }
        if (say != null && say.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                sayTyped();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return say.keyPressed(key, scan, mods) || say.canConsumeInput() || super.keyPressed(key, scan, mods);
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
        if (say != null) {
            say.tick();
        }
        if (view.ended) {
            return; // the closing line stays until the player closes the box
        }
        // The sheet's field is always ready: if anything (a trade screen closing) took the hand away,
        // it comes back to the words.
        if (say != null && getFocused() != say) {
            setFocused(say);
        }
        Entity e = npc();
        Minecraft mc = Minecraft.getInstance();
        if (e == null || !e.isAlive()) {
            // Never silently. The person has gone: the box says so; the server lets go.
            EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "bye", "", ""));
            update(TalkView.ended(view.entityId, view.npcName, view.job, view.level, view.dateLabel,
                    npcNameIsBlank(view.npcName) ? "The person you were talking to has left." : view.npcName + " has left."));
            return;
        }
        if (mc.player == null || e.distanceToSqr(mc.player) > MAX_DISTANCE_SQ) {
            if (mc.player != null && !view.npcName.isBlank()) {
                mc.player.displayClientMessage(Component.literal("You walked away from " + view.npcName + "."), true);
            }
            onClose();
        }
    }

    private static boolean npcNameIsBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        String typed = say == null ? "" : say.getValue();
        super.resize(mc, w, h);
        if (say != null) {
            say.setValue(typed);
        }
    }

    @Override
    public void onClose() {
        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "bye", "", ""));
        super.onClose();
    }

    private void sayTyped() {
        String typed = say == null ? "" : say.getValue().trim();
        if (!typed.isEmpty()) {
            send("free", typed, "");
        }
    }

    private void send(String action, String a, String b) {
        if (view.ended) {
            return; // nobody to ask
        }
        // A short cool-down against a double-tap; long enough to dedupe, short enough that typing feels live.
        if (waitingSince > 0 && System.currentTimeMillis() - waitingSince < 500) {
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
