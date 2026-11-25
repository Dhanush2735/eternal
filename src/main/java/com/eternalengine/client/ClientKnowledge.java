package com.eternalengine.client;

import com.eternalengine.client.gui.TalkBoxScreen;
import com.eternalengine.client.gui.TalkScreen;
import com.eternalengine.client.gui.TradeMenuScreen;
import com.eternalengine.knowledge.TalkView;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client only: shows the talk screen, or updates it when it is already open for the same villager. */
@OnlyIn(Dist.CLIENT)
public final class ClientKnowledge {

    private ClientKnowledge() {
    }

    public static void show(TalkView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkBoxScreen box && box.entityId() == view.entityId) {
            box.update(view);
            return;
        }
        if (mc.screen instanceof TalkScreen talk && talk.entityId() == view.entityId) {
            talk.update(view);
            return;
        }
        // Step 21: the closing line ("Lakshmi has left.") only replaces this person's own talk or trade screen (step 24:
        // the trade screen has just been closed by the server). If the player closed it, or talks to somebody else,
        // it is not shown.
        boolean trading = mc.screen instanceof TradeMenuScreen trade && trade.entityId() == view.entityId;
        if (view.ended && !trading && !TradeMenuScreen.closedRecently(view.entityId)) {
            return;
        }
        if (mc.screen == null || mc.screen instanceof TalkScreen || mc.screen instanceof TalkBoxScreen || trading) {
            mc.setScreen(new TalkBoxScreen(view));
        }
    }

    /** The sheet's trade screen (the approved prototype's open trade): over the talk box. */
    public static void showPanel(com.eternalengine.knowledge.TradePanelView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.eternalengine.client.gui.TradePanelScreen panel && panel.entityId() == view.entityId) {
            panel.update(view);
            return;
        }
        mc.setScreen(new com.eternalengine.client.gui.TradePanelScreen(view));
    }

    /** The person register (the approved prototype's "Who is he?" screen): over the talk box. */
    public static void showRegister(com.eternalengine.knowledge.RegisterView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.eternalengine.client.gui.RegisterScreen reg && reg.entityId() == view.entityId) {
            reg.update(view);
            return;
        }
        mc.setScreen(new com.eternalengine.client.gui.RegisterScreen(view));
    }

    /** Step 24: what the trade screen shows now (sent after every change of the slots, a pick or a trade). */
    public static void showTrade(com.eternalengine.money.TradeView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TradeMenuScreen trade && trade.getMenu().containerId == view.containerId) {
            trade.update(view);
            return;
        }
        if (mc.player != null && mc.player.containerMenu instanceof com.eternalengine.money.TradeMenu m
                && m.containerId == view.containerId) {
            m.setView(view);
        }
    }

    /** Step 19d: a newspaper the player reads. */
    public static void showPaper(com.eternalengine.news.PaperView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof com.eternalengine.client.gui.PaperScreen
                || mc.screen instanceof com.eternalengine.client.gui.MapScreen) {
            mc.setScreen(new com.eternalengine.client.gui.PaperScreen(view));
        }
    }

    /** Step 20: the history book (a new page of it when it is open). */
    public static void showBook(com.eternalengine.knowledge.BookView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.eternalengine.client.gui.BookScreen book) {
            book.update(view);
            return;
        }
        if (mc.screen == null || mc.screen instanceof com.eternalengine.client.gui.PaperScreen
                || mc.screen instanceof com.eternalengine.client.gui.MapScreen) {
            mc.setScreen(new com.eternalengine.client.gui.BookScreen(view));
        }
    }

    /** Step 19e: a map the player opens. */
    public static void showMap(com.eternalengine.news.MapView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof com.eternalengine.client.gui.PaperScreen
                || mc.screen instanceof com.eternalengine.client.gui.MapScreen) {
            mc.setScreen(new com.eternalengine.client.gui.MapScreen(view));
        }
    }
}
