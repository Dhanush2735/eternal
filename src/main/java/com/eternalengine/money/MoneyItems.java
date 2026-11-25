package com.eternalengine.money;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.eternalengine.EternalEngineMod;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Step 23b: the items of money, one for each look (gold coin, note, cowries, cacao beans, cut silver...). */
public final class MoneyItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EternalEngineMod.MOD_ID);

    /** Every look of a piece of money. */
    public static final Map<Denominations.Form, RegistryObject<Item>> PIECES;

    static {
        Map<Denominations.Form, RegistryObject<Item>> m = new EnumMap<>(Denominations.Form.class);
        for (Denominations.Form f : Denominations.Form.values()) {
            m.put(f, ITEMS.register(f.itemId, () -> new MoneyItem(f)));
        }
        PIECES = Collections.unmodifiableMap(m);
    }

    private MoneyItems() {
    }

    /** The item of one look. */
    public static Item item(Denominations.Form form) {
        return PIECES.get(form).get();
    }
}
