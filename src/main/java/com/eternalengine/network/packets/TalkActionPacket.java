package com.eternalengine.network.packets;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.knowledge.TalkService;
import com.eternalengine.knowledge.TalkView;
import com.eternalengine.network.EternalEnginePacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client to server: the player did something on the talk screen (picked a question, clicked a name,
 * typed a name, went back, said goodbye). The server answers with the next {@link TalkViewPacket}.
 */
public class TalkActionPacket implements BasePacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(TalkActionPacket.class);
    /** The player must stay close to the villager: 8 blocks. */
    private static final double MAX_DISTANCE_SQ = 8.0d * 8.0d;

    private int entityId;
    private String action = "";
    private String a = "";
    private String b = "";

    /** Required by the packet handler's decoder. */
    public TalkActionPacket() {
    }

    public TalkActionPacket(int entityId, String action, String a, String b) {
        this.entityId = entityId;
        this.action = action == null ? "" : action;
        this.a = a == null ? "" : a;
        this.b = b == null ? "" : b;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(action, 16);
        buf.writeUtf(a, 96);
        buf.writeUtf(b, 96);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        action = buf.readUtf(16);
        a = buf.readUtf(96);
        b = buf.readUtf(96);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        if ("bye".equals(action)) {
            TalkService.forget(player.getUUID());
            return;
        }
        if (!TalkService.allow(player.getUUID())) {
            return;
        }
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof HumanNpcEntity npc) || !npc.isAlive() || npc.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            // Step 21: never silence. The screen says why the talk is over ("Lakshmi has left.", or the player is too
            // far away), and the talk and the hold on the person end.
            boolean there = e instanceof HumanNpcEntity alive && alive.isAlive();
            EternalEnginePacketHandler.sendToPlayer(new TalkViewPacket(TalkService.ended(player.getUUID(), entityId, there)),
                    player);
            return;
        }
        // Step 21: the person stays while the player talks or trades (the hold starts again from this action).
        com.eternalengine.knowledge.TalkHold.hold(player, npc);
        try {
            if ("trade".equals(action) || "counter".equals(action)) {
                // The old trade screen first (owner's choice, 2026-11-16): Minecraft's
                // trade menu with real items and working slots (TradeMenuScreen),
                // wherever this person can trade on it. Only where it cannot open
                // does the era panel below stand.
                com.eternalengine.knowledge.TalkService.Ctx c = TalkService.context(player, npc);
                if (com.eternalengine.money.TradeMenu.open(player, npc)) {
                    return;
                }
                // The sheet's red box: the sheet's trade screen (the approved prototype's open
                // trade), for every role that holds a counter — the person's line (the standing
                // decides it), the goods and prices of the place-year, or the honest line. Opening
                // it is the trade. Where the sheet has no counter, the old ways stand.
                if (TalkService.counterRole(TalkService.roleOf(c))) {
                    com.eternalengine.civilization.ReputationStanding store = com.eternalengine.civilization.ReputationStanding.get(player.getServer().overworld());
                    com.eternalengine.civilization.ReputationStanding.Standing ps = store.standing(player.getUUID(), TalkService.dayOf(c));
                    com.eternalengine.civilization.ReputationStanding.Standing.Person p = store.person(player.getUUID(), npc.getUUID(), TalkService.dayOf(c));
                    com.eternalengine.knowledge.TradePanelView pv = com.eternalengine.knowledge.EraTradePanel.panel(c,
                            com.eternalengine.civilization.ReputationStanding.tier(ps.posRep, ps.negRep));
                    if (pv != null) {
                        com.eternalengine.knowledge.EraTradePanel.exec(c, p);
                        EternalEnginePacketHandler.sendToPlayer(new EraPanelPacket(EraPanelPacket.PANEL, pv, null), player);
                        return;
                    }
                }
                // The old screen could not open and no counter panel stands:
                // the counter line in the talk box, or the honest line.
                TalkView v = TalkService.eraTrade(c);
                if (v == null) {
                    v = TalkService.act(c, "show", "", "");
                    if (v != null) {
                        v.hint = "I have nothing to trade.";
                    }
                }
                if (v != null) {
                    EternalEnginePacketHandler.sendToPlayer(new TalkViewPacket(v), player);
                }
                return;
            }
            if ("register".equals(action)) {
                // The sheet's "Who is he?" (and the portrait): the person register, its five pages
                // and the tally of its marks.
                com.eternalengine.knowledge.TalkService.Ctx c = TalkService.context(player, npc);
                com.eternalengine.civilization.ReputationStanding store = com.eternalengine.civilization.ReputationStanding.get(player.getServer().overworld());
                com.eternalengine.civilization.ReputationStanding.Standing ps = store.standing(player.getUUID(), TalkService.dayOf(c));
                com.eternalengine.civilization.ReputationStanding.Standing.Person p = store.person(player.getUUID(), npc.getUUID(), TalkService.dayOf(c));
                com.eternalengine.knowledge.RegisterView rv = com.eternalengine.knowledge.EraRegister.build(c, p,
                        com.eternalengine.civilization.ReputationStanding.tier(ps.posRep, ps.negRep),
                        com.eternalengine.knowledge.EraTables.hourOf(player.level().getGameTime()));
                if (rv != null) {
                    EternalEnginePacketHandler.sendToPlayer(new EraPanelPacket(EraPanelPacket.REGISTER, null, rv), player);
                }
                return;
            }
            if ("panelclose".equals(action)) {
                // Back from the sheet's screens: the talk as it was (the sheet's closeTrade — the talk goes on).
                TalkView view = TalkService.act(TalkService.context(player, npc), "show", "", "");
                if (view != null) {
                    EternalEnginePacketHandler.sendToPlayer(new TalkViewPacket(view), player);
                }
                return;
            }
            if ("show".equals(action) && player.containerMenu instanceof com.eternalengine.money.TradeMenu tm) {
                // Step 24: "‹ Back to ..." on the trade screen: close it (what lies in its slots goes back into the
                // inventory) without ending the talk, then the talk box as it was.
                tm.backToTalk();
                player.closeContainer();
                com.eternalengine.knowledge.TalkHold.hold(player, npc);
            }
            TalkView view = TalkService.act(TalkService.context(player, npc), action, a, b);
            if (view != null) {
                // The sheet's trade screen, opened from the typed trade words: its own packet,
                // after the talk state (the screen shows over the talk box).
                if (view.tradePanel != null) {
                    com.eternalengine.knowledge.TradePanelView pv = view.tradePanel;
                    view.tradePanel = null;
                    EternalEnginePacketHandler.sendToPlayer(new TalkViewPacket(view), player);
                    EternalEnginePacketHandler.sendToPlayer(new EraPanelPacket(EraPanelPacket.PANEL, pv, null), player);
                    return;
                }
                EternalEnginePacketHandler.sendToPlayer(new TalkViewPacket(view), player);
            }
        } catch (Exception ex) {
            LOGGER.warn("[EE Talk] Could not answer {} {} {}: {}", action, a, b, ex.toString());
        }
    }
}
