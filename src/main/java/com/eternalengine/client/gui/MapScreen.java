package com.eternalengine.client.gui;

import com.eternalengine.client.gui.paint.MapPainter;
import com.eternalengine.client.gui.paint.TradePainter;
import com.eternalengine.news.MapView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Step 19e: a map (drawn by {@link MapPainter}). Click a town to see what your papers said about it; "Add to Xaero's
 * map" makes a waypoint with Xaero's own share link (and writes the place in the chat), or, without Xaero, writes the
 * place's x and z in the chat.
 */
@OnlyIn(Dist.CLIENT)
public class MapScreen extends Screen {

    private final MapView view;
    private final MapPainter.State state = new MapPainter.State();
    private List<TradePainter.Hot> hots = List.of();
    private final boolean xaero;

    public MapScreen(MapView view) {
        super(Component.literal(view.title));
        this.view = view;
        this.xaero = ModList.get() != null && (ModList.get().isLoaded("xaerominimap")
                || ModList.get().isLoaded("xaerobetterpvp"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hots = MapPainter.paint(new GuiCanvas(g, font), view, state, width, height, mouseX, mouseY, xaero);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (TradePainter.Hot h : hots) {
                if (!h.contains(mx, my)) {
                    continue;
                }
                switch (h.action()) {
                    case "select" -> {
                        state.selected = h.arg();
                        state.message = "";
                    }
                    case "xaero" -> waypoint(h.arg());
                    case "close" -> onClose();
                    default -> {
                    }
                }
                return true;
            }
            state.selected = "";
            state.message = "";
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Xaero's share format: xaero-waypoint:name:initials:x:y:z:colour:false:0:Internal_overworld_waypoints. */
    static String waypointCommand(String name, int x, int y, int z) {
        String n = name.replace(":", " ").replace("§", "").trim();
        String initial = n.isEmpty() ? "X" : n.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        return "/xaero_waypoint_add:" + n + ":" + initial + ":" + x + ":" + y + ":" + z + ":6:false:0"
                + ":Internal_overworld_waypoints";
    }

    private void waypoint(String name) {
        MapView.Town town = null;
        for (MapView.Town t : view.towns) {
            if (t.name().equalsIgnoreCase(name)) {
                town = t;
            }
        }
        int x, z;
        if (town != null) {
            x = town.x();
            z = town.z();
        } else {
            double[] p = MapPainter.position(view, name);
            if (p == null) {
                return;
            }
            x = 0;
            z = 0;
            try {
                double[] xz = com.eternalengine.terrabridge.TerraCoordinateHelper.fromGeo(p[0], p[1]);
                x = (int) Math.round(xz[0]);
                z = (int) Math.round(xz[1]);
            } catch (Throwable ignored) {
                // No projection here.
            }
        }
        Minecraft mc = Minecraft.getInstance();
        int y = mc.player == null ? 64 : mc.player.getBlockY();
        String cmd = waypointCommand(name, x, y, z);
        MutableComponent line = Component.literal("[" + name + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("x " + x + ", z " + z).withStyle(ChatFormatting.WHITE));
        if (xaero) {
            line.append(Component.literal(" "));
            line.append(Component.literal("[Add to Xaero's map]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("A waypoint for " + name)))));
        }
        mc.gui.getChat().addMessage(line);
        if (xaero) {
            // The same thing as clicking Xaero's own share link in the chat.
            handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
            state.message = "Added " + name + " to Xaero's map (and wrote it in the chat).";
        } else {
            state.message = "Xaero's map is not installed. " + name + " is at x " + x + ", z " + z + " (written in the chat).";
        }
    }
}
