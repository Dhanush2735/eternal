package com.eternalengine.network.packets;

import com.eternalengine.knowledge.BookView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client. Step 20: open (or turn the page of) the player's history book. */
public class BookViewPacket implements BasePacket {

    private BookView view = new BookView();

    /** Required by the packet handler's decoder. */
    public BookViewPacket() {
    }

    public BookViewPacket(BookView view) {
        this.view = view;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        view = BookView.decode(buf);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        BookView v = view;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.ClientKnowledge.showBook(v));
    }

    public BookView getView() {
        return view;
    }
}
