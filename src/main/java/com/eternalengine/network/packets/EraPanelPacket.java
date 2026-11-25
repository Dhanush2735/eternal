package com.eternalengine.network.packets;

import com.eternalengine.knowledge.RegisterView;
import com.eternalengine.knowledge.TradePanelView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one of the sheet's two other screens — the trade screen (the sheet's open
 * trade, with the goods and prices of the place-year) or the person register (the "Who is he?"
 * screen). The talk box stays behind it; back or close returns to it.
 */
public class EraPanelPacket implements BasePacket {

    public static final int PANEL = 0;
    public static final int REGISTER = 1;

    private int type = PANEL;
    private TradePanelView panel;
    private RegisterView register;

    /** Required by the packet handler's decoder. */
    public EraPanelPacket() {
    }

    public EraPanelPacket(int type, TradePanelView panel, RegisterView register) {
        this.type = type;
        this.panel = panel;
        this.register = register;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        if (type == PANEL) {
            panel.encode(buf);
        } else {
            register.encode(buf);
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        type = buf.readByte();
        if (type == PANEL) {
            panel = TradePanelView.decode(buf);
        } else {
            register = RegisterView.decode(buf);
        }
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (type == PANEL) {
                com.eternalengine.client.ClientKnowledge.showPanel(panel);
            } else {
                com.eternalengine.client.ClientKnowledge.showRegister(register);
            }
        });
    }
}
