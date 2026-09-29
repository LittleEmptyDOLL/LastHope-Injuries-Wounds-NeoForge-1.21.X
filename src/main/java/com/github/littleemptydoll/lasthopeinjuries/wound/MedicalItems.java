package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Basic medicine is available even when LSO is absent; modpacks may extend the treatment tag. */
public final class MedicalItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, LastHopeInjuries.MOD_ID);
    public static final Supplier<Item> ANTIBIOTICS = ITEMS.register(
            "antibiotics", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> SUTURES = ITEMS.register(
            "sutures", () -> new Item(new Item.Properties().stacksTo(16)));

    private MedicalItems() {}
}
