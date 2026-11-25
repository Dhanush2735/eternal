package com.eternalengine.network.packets;

import com.eternalengine.news.MapView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client. Step 19e: open a map the player looks at. */
public class MapViewPacket implements BasePacket {

    private MapView view = new MapView();

    /** Required by the packet handler's decoder. */
    public MapViewPacket() {
    }

    public MapViewPacket(MapView view) {
        this.view = view;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        view = MapView.decode(buf);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        MapView v = view;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.ClientKnowledge.showMap(v));
    }

    public MapView getView() {
        return view;
    }
}
