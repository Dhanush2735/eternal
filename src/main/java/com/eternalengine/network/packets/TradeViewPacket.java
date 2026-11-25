package com.eternalengine.network.packets;

import com.eternalengine.money.TradeView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client. Step 19c: open or update the trade screen with a seller. */
public class TradeViewPacket implements BasePacket {

    private TradeView view = new TradeView();

    /** Required by the packet handler's decoder. */
    public TradeViewPacket() {
    }

    public TradeViewPacket(TradeView view) {
        this.view = view;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        view = TradeView.decode(buf);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        TradeView v = view;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.ClientKnowledge.showTrade(v));
    }

    public TradeView getView() {
        return view;
    }
}
