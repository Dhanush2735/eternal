package com.eternalengine.money;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.knowledge.TalkHold;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TradeViewPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * Step 24: Minecraft's trade screen with a townsperson, as a real container: the trades on the left, two slots to pay
 * with, the slot to take from, and the player's inventory, laid out as the villager's trade screen. The server decides
 * everything ({@link TradeDesk}); the client shows the slots and the view the server sends ({@link TradeView}).
 *
 * <p>Click a trade: the slots fill from the inventory. Take the thing from the slot on the right: you pay, and change
 * comes back into the inventory. Shift-click it to trade again and again while the slots pay. Close the screen and
 * what lies in the two slots goes back into the inventory.
 */
public class TradeMenu extends AbstractContainerMenu {

    public static final int PAY_A = 0;
    public static final int PAY_B = 1;
    public static final int RESULT = 2;
    public static final int INV_START = 3;
    public static final int USE_START = 30;
    public static final int INV_END = 39;

    private final SimpleContainer tray = new SimpleContainer(2);
    private final ResultContainer result = new ResultContainer();
    private final Player player;
    @Nullable
    private final TradeDesk desk;
    private final int entityId;
    private TradeView view = new TradeView();
    private boolean backToTalk;

    /** On the server: the trade screen of a townsperson. */
    public TradeMenu(int id, Inventory inv, TradeDesk desk) {
        this(id, inv, desk, desk.entityId());
    }

    private TradeMenu(int id, Inventory inv, @Nullable TradeDesk desk, int entityId) {
        super(TradeMenus.TRADE.get(), id);
        this.player = inv.player;
        this.desk = desk;
        this.entityId = entityId;
        this.view.entityId = entityId;
        this.view.containerId = id;
        this.addSlot(new Slot(tray, 0, 136, 37));
        this.addSlot(new Slot(tray, 1, 162, 37));
        this.addSlot(new ResultSlot(result, 0, 220, 37));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 108 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 108 + col * 18, 142));
        }
        tray.addListener(this::slotsChanged);
        if (desk != null) {
            desk.rebuild(tray);
            desk.update(this);
        }
    }

    /** On the client, from the open-screen message: the townsperson's entity id. */
    public static TradeMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        return new TradeMenu(id, inv, null, buf.readVarInt());
    }

    /**
     * Opens the trade screen of this person for this player (from the talk box). False when the person does not
     * trade.
     */
    public static boolean open(ServerPlayer p, HumanNpcEntity npc) {
        TradeDesk desk = TradeDesk.create(p, npc);
        if (desk == null) {
            return false;
        }
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new TradeMenu(id, inv, desk),
                Component.literal(desk.title())), buf -> buf.writeVarInt(npc.getId()));
        TalkHold.hold(p, npc); // opening closed any other screen, which let go of the person
        p.containerMenu.broadcastChanges();
        return true;
    }

    /**
     * The same trade screen on the server only, for the test helper (a fake player has no screen and no connection):
     * it becomes the player's open menu.
     */
    @Nullable
    public static TradeMenu openHere(ServerPlayer p, HumanNpcEntity npc) {
        TradeDesk desk = TradeDesk.create(p, npc);
        if (desk == null) {
            return null;
        }
        TradeMenu m = new TradeMenu(p.containerMenu.containerId % 100 + 1, p.getInventory(), desk);
        p.containerMenu = m;
        TalkHold.hold(p, npc);
        return m;
    }

    // -------------------------------------------------------------------------------------- for the desk

    Container tray() {
        return tray;
    }

    void showResult(ItemStack stack) {
        result.setItem(0, stack == null ? ItemStack.EMPTY : stack);
    }

    /** Puts what lies in the two slots back into the inventory, as far as it fits. */
    void emptyTray() {
        for (int slot = 0; slot < 2; slot++) {
            ItemStack s = tray.getItem(slot);
            if (s.isEmpty()) {
                continue;
            }
            moveItemStackTo(s, INV_START, INV_END, true);
            tray.setItem(slot, s.isEmpty() ? ItemStack.EMPTY : s);
        }
    }

    // ------------------------------------------------------------------------------------- for the screen

    /** The townsperson's entity id. */
    public int entityId() {
        return entityId;
    }

    /** The latest view from the server (client), or an empty one. */
    public TradeView view() {
        return view;
    }

    public void setView(TradeView v) {
        if (v != null) {
            this.view = v;
        }
    }

    /** The server side of this screen (null on the client), for the test helper. */
    @Nullable
    public TradeDesk desk() {
        return desk;
    }

    /** The player goes back to the talk: closing this screen does not end the talk. */
    public void backToTalk() {
        this.backToTalk = true;
    }

    /** The server's view now (for the test helper and checks). */
    @Nullable
    public TradeView serverView() {
        return desk == null ? null : desk.view(containerId, tray);
    }

    // ------------------------------------------------------------------------------------------- the menu

    @Override
    public void slotsChanged(Container c) {
        if (desk != null && c == tray) {
            desk.update(this);
        }
        super.slotsChanged(c);
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (desk != null) {
            desk.select(this, id);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        return desk == null || desk.valid(p);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (desk != null && desk.dirty && player instanceof ServerPlayer sp && !TradeDesk.fake(sp)) {
            EternalEnginePacketHandler.sendToPlayer(new TradeViewPacket(desk.view(containerId, tray)), sp);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            if (index == RESULT) {
                if (!slot.mayPickup(p) || !this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, copy);
                // The trade is done: the city notes it as a good deed for the player (the sheet's
                // "a done trade is a done thing"), and the man remembers that the two have traded.
                if (desk != null && p instanceof ServerPlayer sp && !TradeDesk.fake(sp) && sp.getServer() != null) {
                    java.util.UUID npcId = sp.level().getEntity(entityId) != null
                            ? sp.level().getEntity(entityId).getUUID()
                            : null;
                    com.eternalengine.civilization.ReputationStanding store =
                            com.eternalengine.civilization.ReputationStanding.get(sp.getServer().overworld());
                    store.tradeDone(sp.getUUID(), npcId, com.eternalengine.knowledge.KnowledgeDate.today(), 10);
                }
            } else if (index != PAY_A && index != PAY_B) {
                // From the inventory: money into the two slots first (the same rule on both sides, so the client's
                // guess is the server's answer), anything else between the bag and the hotbar, as in Minecraft.
                boolean pays = MoneyItem.isMoney(stack);
                if (!(pays && this.moveItemStackTo(stack, PAY_A, RESULT, false))) {
                    if (index < USE_START) {
                        if (!this.moveItemStackTo(stack, USE_START, INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, INV_START, USE_START, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(p, stack);
        }
        return copy;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (desk == null) {
            return;
        }
        if (!p.isAlive() || p instanceof ServerPlayer sp && sp.hasDisconnected()) {
            for (int slot = 0; slot < 2; slot++) {
                ItemStack s = tray.removeItemNoUpdate(slot);
                if (!s.isEmpty()) {
                    p.drop(s, false);
                }
            }
        } else {
            p.getInventory().placeItemBackInInventory(tray.removeItemNoUpdate(0));
            p.getInventory().placeItemBackInInventory(tray.removeItemNoUpdate(1));
        }
        result.setItem(0, ItemStack.EMPTY);
        desk.closed(p, backToTalk);
    }

    /** The slot to take from: nothing goes in; taking it is the trade. */
    final class ResultSlot extends Slot {
        ResultSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return desk == null || desk.canTake(TradeMenu.this);
        }

        @Override
        public ItemStack remove(int amount) {
            return super.remove(getItem().getCount()); // always all of it: half a thing is not for half the price
        }

        @Override
        public void onTake(Player p, ItemStack stack) {
            if (desk != null) {
                desk.take(TradeMenu.this);
            }
            super.onTake(p, stack);
        }
    }
}
