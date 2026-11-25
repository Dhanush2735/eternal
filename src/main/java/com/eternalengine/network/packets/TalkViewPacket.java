package com.eternalengine.network.packets;

import com.eternalengine.knowledge.TalkView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: open or update the talk screen with a villager (step 18). */
public class TalkViewPacket implements BasePacket {

    private TalkView view = new TalkView();

    /** Required by the packet handler's decoder. */
    public TalkViewPacket() {
    }

    public TalkViewPacket(TalkView view) {
        this.view = view;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        view = TalkView.decode(buf);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        TalkView v = view;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.ClientKnowledge.show(v));
    }

    public TalkView getView() {
        return view;
    }
}
