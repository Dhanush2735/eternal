package com.eternalengine.network.packets;

import com.eternalengine.news.PaperView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client. Step 19d: open a newspaper the player reads. */
public class PaperViewPacket implements BasePacket {

    private PaperView view = new PaperView();

    /** Required by the packet handler's decoder. */
    public PaperViewPacket() {
    }

    public PaperViewPacket(PaperView view) {
        this.view = view;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        view = PaperView.decode(buf);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        PaperView v = view;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.ClientKnowledge.showPaper(v));
    }

    public PaperView getView() {
        return view;
    }
}
