package com.eternalengine.client.gui;

import com.eternalengine.timeline.ChronosCodexData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive parchment book GUI for the Chronos Codex.
 * Allows players to browse timeline divergence trees, geopolitical atlases,
 * VIP dossiers, and daily global chronicles.
 */
@OnlyIn(Dist.CLIENT)
public class ChronosCodexScreen extends Screen {

    private ChronosCodexData.CodexTab activeTab = ChronosCodexData.CodexTab.TIMELINE_TREE;
    private List<String> currentPages = new ArrayList<>();
    private int pageIndex = 0;

    public ChronosCodexScreen() {
        super(Component.literal("The Chronos Codex"));
    }

    @Override
    protected void init() {
        super.init();
        int btnY = 16;
        int btnW = 90;
        int startX = (this.width - (btnW * 4 + 18)) / 2;

        // Navigation Tabs
        addRenderableWidget(Button.builder(Component.literal("Timeline Tree"), b -> setTab(ChronosCodexData.CodexTab.TIMELINE_TREE))
            .bounds(startX, btnY, btnW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Atlas"), b -> setTab(ChronosCodexData.CodexTab.ATLAS))
            .bounds(startX + btnW + 6, btnY, btnW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("VIP Dossier"), b -> setTab(ChronosCodexData.CodexTab.DOSSIER))
            .bounds(startX + (btnW + 6) * 2, btnY, btnW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Chronicle"), b -> setTab(ChronosCodexData.CodexTab.CHRONICLE))
            .bounds(startX + (btnW + 6) * 3, btnY, btnW, 20).build());

        // Page navigation buttons
        int pageBtnY = this.height - 35;
        addRenderableWidget(Button.builder(Component.literal("◄ Prev"), b -> prevPage())
            .bounds(this.width / 2 - 110, pageBtnY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Next ►"), b -> nextPage())
            .bounds(this.width / 2 + 40, pageBtnY, 70, 20).build());

        loadCurrentTabContent();
    }

    private void setTab(ChronosCodexData.CodexTab tab) {
        this.activeTab = tab;
        this.pageIndex = 0;
        loadCurrentTabContent();
    }

    private void prevPage() {
        if (pageIndex > 0) pageIndex--;
    }

    private void nextPage() {
        if (pageIndex < currentPages.size() - 1) pageIndex++;
    }

    private void loadCurrentTabContent() {
        switch (activeTab) {
            case TIMELINE_TREE -> currentPages = ChronosCodexData.buildTimelineTreePages(null);
            case ATLAS -> currentPages = ChronosCodexData.buildAtlasPages(null, 2026);
            case DOSSIER -> currentPages = ChronosCodexData.buildDossierPages();
            case CHRONICLE -> currentPages = ChronosCodexData.buildChroniclePages(2026);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Dark translucent parchment background
        renderBackground(graphics);

        int bookW = Math.min(360, this.width - 40);
        int bookH = this.height - 90;
        int bookX = (this.width - bookW) / 2;
        int bookY = 45;

        // Render aged parchment folio box
        graphics.fill(bookX - 4, bookY - 4, bookX + bookW + 4, bookY + bookH + 4, 0xFF4A3525); // Leather border
        graphics.fill(bookX, bookY, bookX + bookW, bookY + bookH, 0xFFEEDAA2); // Parchment paper

        // Render current page content
        if (!currentPages.isEmpty() && pageIndex < currentPages.size()) {
            String pageText = currentPages.get(pageIndex);
            String[] lines = pageText.split("\n");
            int textY = bookY + 12;
            for (String line : lines) {
                graphics.drawString(this.font, line, bookX + 16, textY, 0xFF1A1A1A, false);
                textY += 11;
                if (textY > bookY + bookH - 20) break;
            }
        }

        // Render page indicator
        String pageIndicator = "Page " + (pageIndex + 1) + " of " + Math.max(1, currentPages.size());
        graphics.drawCenteredString(this.font, pageIndicator, this.width / 2, this.height - 30, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
