package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Item tags can be extended by the modpack without depending on another mod's Java API. */
public final class TreatmentItems {
    public static final TagKey<Item> BANDAGES = tag("bandages");
    public static final TagKey<Item> ANTISEPTICS = tag("antiseptics");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, path));
    }

    private TreatmentItems() {}
}
