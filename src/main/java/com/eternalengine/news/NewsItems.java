package com.eternalengine.news;

import com.eternalengine.EternalEngineMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Step 19d and 19e: the goods a player buys to know what is going on: a newspaper, a newsletter, a map. */
public final class NewsItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EternalEngineMod.MOD_ID);

    /** A printed newspaper of one day. */
    public static final RegistryObject<Item> NEWSPAPER = ITEMS.register("newspaper", () -> new NewspaperItem(false));
    /** A handwritten newsletter, from the time before print. */
    public static final RegistryObject<Item> NEWSLETTER = ITEMS.register("newsletter", () -> new NewspaperItem(true));
    /** A map of the land around a town: its towns and roads in its year, and the places from the papers read with it. */
    public static final RegistryObject<Item> MAP = ITEMS.register("map", MapItem::new);
    /** Step 20: the history book: the player's own notes (kept with the world, not on the item). Step 22: the notebook. */
    public static final RegistryObject<Item> HISTORY_BOOK = ITEMS.register("history_book",
            () -> new HistoryBookItem(BookForms.Form.NOTEBOOK));
    /** Step 22: every form of the history book (the notebook is {@link #HISTORY_BOOK}). */
    public static final Map<BookForms.Form, RegistryObject<Item>> HISTORY_BOOKS;

    static {
        Map<BookForms.Form, RegistryObject<Item>> m = new EnumMap<>(BookForms.Form.class);
        for (BookForms.Form f : BookForms.Form.values()) {
            m.put(f, f == BookForms.Form.NOTEBOOK ? HISTORY_BOOK : ITEMS.register(f.itemId(), () -> new HistoryBookItem(f)));
        }
        HISTORY_BOOKS = Collections.unmodifiableMap(m);
    }

    private NewsItems() {
    }

    /** The item of one form of the history book. */
    public static Item book(BookForms.Form form) {
        return HISTORY_BOOKS.get(form == null ? BookForms.Form.NOTEBOOK : form).get();
    }
}
