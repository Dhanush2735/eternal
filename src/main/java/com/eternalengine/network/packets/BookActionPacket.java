package com.eternalengine.network.packets;

import com.eternalengine.knowledge.BookService;
import com.eternalengine.knowledge.TalkService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client to server. Step 20: the player did something in the history book (opened a name, a tab, tracked a place,
 * stopped tracking). The server answers with the next {@link BookViewPacket}.
 */
public class BookActionPacket implements BasePacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookActionPacket.class);
    private String action = "";
    private String card = "";
    private String tab = "";

    /** Required by the packet handler's decoder. */
    public BookActionPacket() {
    }

    public BookActionPacket(String action, String card, String tab) {
        this.action = action == null ? "" : action;
        this.card = card == null ? "" : card;
        this.tab = tab == null ? "" : tab;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 16);
        buf.writeUtf(card, 96);
        buf.writeUtf(tab, 16);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        action = buf.readUtf(16);
        card = buf.readUtf(96);
        tab = buf.readUtf(16);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || !TalkService.allow(player.getUUID())) {
            return;
        }
        if (!BookService.mayRead(player)) {
            return; // step 22: the notes are read with a history book
        }
        try {
            BookService.act(player, action, card, tab);
        } catch (Exception ex) {
            LOGGER.warn("[EE book] Could not answer {} {} {}: {}", action, card, tab, ex.toString());
        }
    }
}
