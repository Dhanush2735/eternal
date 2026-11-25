package com.eternalengine.client.gui;

import com.eternalengine.knowledge.RegisterView;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TalkActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The person register (the approved prototype's "Who is he?" screen): the masthead, the
 * place-ruler-date line, what is in his mind, the five pages, and the tally of the page's
 * marks — every line wears its mark (recorded, estimated, believed, not recorded, not yet),
 * and the page is silent where it is silent.
 */
public class RegisterScreen extends net.minecraft.client.gui.screens.Screen {

    private static final int BG = 0xE6141410;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int GOLD = 0xFFFFD75E;
    private static final int GRAY = 0xFFA8A8B0;
    private static final int DIM = 0xFF8A8A94;
    private static final int LINE = 10;
    private static final int MARGIN = 14;

    private RegisterView view;
    private int tab = 0;
    private int scroll = 0;
    private int sheetX, sheetY, sheetW, sheetH;
    private int contentY, contentH;
    private int closeX, closeY;
    private final List<int[]> tabBoxes = new ArrayList<>();
    private int contentLines = 0;

    public RegisterScreen(RegisterView view) {
        super(Component.literal("Person register"));
        this.view = view;
    }

    public int entityId() {
        return view.entityId;
    }

    public void update(RegisterView next) {
        this.view = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        sheetW = Math.min(640, Math.max(420, width - 40));
        sheetX = (width - sheetW) / 2;
        sheetY = 24;
        sheetH = Math.min(480, height - 48);
        contentY = sheetY + 118;
        contentH = sheetH - 118 - 64;
        closeX = sheetX + sheetW - 70;
        closeY = sheetY + 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(sheetX, sheetY, sheetX + sheetW, sheetY + sheetH, BG);
        outline(g, sheetX, sheetY, sheetX + sheetW, sheetY + sheetH, 0xFF6B5A2A);

        // The strip: the person and the copy, and the close. The strip is cut
        // short so it can never run under the close.
        g.drawString(font, fit(view.strip, sheetW - 2 * MARGIN - 70), sheetX + MARGIN, sheetY + 10, DIM, false);
        boolean overClose = mouseX >= closeX && mouseX < closeX + 60 && mouseY >= closeY && mouseY < closeY + 14;
        g.drawString(font, "Close ×", closeX, closeY + 2, overClose ? 0xFFFFFF : GRAY, false);

        // The seal: the place's letters and the century.
        g.fill(sheetX + MARGIN, sheetY + 26, sheetX + MARGIN + 34, sheetY + 58, 0x00000000);
        outline(g, sheetX + MARGIN, sheetY + 26, sheetX + MARGIN + 34, sheetY + 58, 0xFF8A7A5A);
        g.drawString(font, fit(view.sealTop, 26), sheetX + MARGIN + 4, sheetY + 30, GOLD, false);
        g.drawString(font, fit(view.sealBottom, 26), sheetX + MARGIN + 4, sheetY + 44, GRAY, false);

        // The masthead, the place-ruler-date line, and what is in his mind.
        // The date line starts after the seal and wraps inside the sheet.
        g.drawString(font, "PERSON REGISTER", sheetX + sheetW / 2 - font.width("PERSON REGISTER") / 2, sheetY + 28, GOLD, false);
        int headX = sheetX + MARGIN + 44;
        int headW = sheetW - 2 * MARGIN - 44;
        int hy = sheetY + 42;
        for (String line : ScreenText.wrap(view.dateRow, headW, font::width)) {
            g.drawString(font, line, headX, hy, TEXT, false);
            hy += LINE;
        }
        g.fill(sheetX + MARGIN, hy + 2, sheetX + sheetW - MARGIN, hy + 3, 0x33FFD75E);
        hy += 10;
        for (String line : ScreenText.wrap("\"" + view.mind + "\"", headW, font::width)) {
            g.drawString(font, line, headX, hy, 0xFFD8C8A8, false);
            hy += LINE;
        }
        for (String line : ScreenText.wrap(view.mindSmall, headW, font::width)) {
            g.drawString(font, line, headX, hy, DIM, false);
            hy += LINE;
        }

        // The five pages, under whatever the head needed.
        tabBoxes.clear();
        int tx = sheetX + MARGIN;
        int ty = hy + 8;
        contentY = ty + 20;
        contentH = Math.max(40, sheetY + sheetH - 64 - contentY);
        for (int i = 0; i < view.tabs.size(); i++) {
            String t = view.tabs.get(i).title();
            int w = font.width(t) + 14;
            boolean on = i == tab;
            boolean over = mouseX >= tx && mouseX < tx + w && mouseY >= ty && mouseY < ty + 14;
            g.fill(tx, ty, tx + w, ty + 14, on ? 0x33FFD75E : (over ? 0x1A4A4A3A : 0x0A3A3A2A));
            outline(g, tx, ty, tx + w, ty + 14, on ? 0xFFC8A850 : 0xFF4A4A3A);
            g.drawString(font, t, tx + 7, ty + 2, on ? GOLD : (over ? 0xE0E0C0 : 0xB0B0A0), false);
            tabBoxes.add(new int[]{tx, ty, w, 14});
            tx += w + 6;
        }

        // The page's lines, under their headings, each with its mark.
        g.enableScissor(sheetX + 2, contentY, sheetX + sheetW - 2, contentY + contentH);
        int y = contentY - scroll;
        contentLines = 0;
        if (tab >= 0 && tab < view.tabs.size()) {
            for (RegisterView.Section s : view.tabs.get(tab).sections()) {
                if (!s.title().isEmpty()) {
                    y = skip(g, y, 4);
                    g.drawString(font, s.title(), sheetX + MARGIN, y, GRAY, false);
                    y += LINE + 2;
                }
                for (RegisterView.Row r : s.rows()) {
                    String label = r.label().isEmpty() ? "" : r.label() + "  ";
                    List<String> lines = ScreenText.wrap(label + r.value(), sheetW - 2 * MARGIN - 10, font::width);
                    for (int li = 0; li < lines.size(); li++) {
                        contentLines++;
                        if (y >= contentY - LINE && y < contentY + contentH + LINE) {
                            if (li == 0 && !label.isEmpty() && lines.get(li).startsWith(label)) {
                                g.drawString(font, label, sheetX + MARGIN, y, TEXT, false);
                                g.drawString(font, lines.get(li).substring(label.length()),
                                        sheetX + MARGIN + font.width(label), y, DIM, false);
                            } else {
                                g.drawString(font, lines.get(li), sheetX + MARGIN, y, DIM, false);
                            }
                        }
                        y += LINE;
                    }
                }
                if (!s.note().isEmpty()) {
                    y = skip(g, y, 4);
                    for (String line : ScreenText.wrap(s.note(), sheetW - 2 * MARGIN, font::width)) {
                        if (y >= contentY - LINE && y < contentY + contentH + LINE) {
                            g.drawString(font, line, sheetX + MARGIN, y, 0xFF7A7A6A, false);
                        }
                        y += LINE;
                    }
                }
            }
        }
        g.disableScissor();

        // The tally and the legend, under the page. Each wraps inside the
        // sheet and stacks, so nothing runs off the right edge.
        int fy = sheetY + sheetH - 58;
        g.fill(sheetX + MARGIN, fy - 4, sheetX + sheetW - MARGIN, fy - 3, 0x33FFD75E);
        String tally = view.lines + " lines — " + view.rec + " recorded · " + view.est + " estimated · "
                + view.bel + " believed · " + view.non + " not recorded · " + view.yet
                + " not yet. No unmarked line.";
        int footW = sheetW - 2 * MARGIN;
        for (String line : ScreenText.wrap(tally, footW, font::width)) {
            g.drawString(font, line, sheetX + MARGIN, fy, TEXT, false);
            fy += LINE;
        }
        g.drawString(font, "The register is silent where it is silent.", sheetX + MARGIN, fy, DIM, false);
        fy += LINE;
        for (String line : ScreenText.wrap("recorded — a register says it · estimated — worked out from records · believed — he holds it, no record has it", footW, font::width)) {
            g.drawString(font, line, sheetX + MARGIN, fy, 0xFF78A868, false);
            fy += LINE;
        }
        for (String line : ScreenText.wrap("not recorded — the register is silent · not yet — awaiting its own go", footW, font::width)) {
            g.drawString(font, line, sheetX + MARGIN, fy, 0xFFA8A088, false);
            fy += LINE;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** An empty line of {@code h} pixels. */
    private int skip(GuiGraphics g, int y, int h) {
        return y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (mx >= closeX && mx < closeX + 60 && my >= closeY && my < closeY + 14) {
                back();
                return true;
            }
            for (int[] tb : tabBoxes) {
                if (mx >= tb[0] && mx < tb[0] + tb[2] && my >= tb[1] && my < tb[1] + tb[3]) {
                    tab = tabBoxes.indexOf(tb);
                    scroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta != 0) {
            int maxScroll = Math.max(0, contentLines * LINE - contentH);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (delta * 12)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Back to the talk: the sheet's closeRegister — the talk goes on where it was. */
    private void back() {
        EternalEnginePacketHandler.sendToServer(new TalkActionPacket(view.entityId, "panelclose", "", ""));
        minecraft.setScreen(null);
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

    private static void outline(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
        g.fill(ax, ay, bx, ay + 1, color);
        g.fill(ax, by - 1, bx, by, color);
        g.fill(ax, ay, ax + 1, by, color);
        g.fill(bx - 1, ay, bx, by, color);
    }
}
