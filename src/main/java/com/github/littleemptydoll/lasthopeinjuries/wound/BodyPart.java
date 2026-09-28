package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EquipmentSlot;

/** These names intentionally correspond to LSO BodyPartEnum's eight regions. */
public enum BodyPart {
    HEAD(EquipmentSlot.HEAD),
    CHEST(EquipmentSlot.CHEST),
    LEFT_ARM(EquipmentSlot.CHEST),
    RIGHT_ARM(EquipmentSlot.CHEST),
    LEFT_LEG(EquipmentSlot.LEGS),
    RIGHT_LEG(EquipmentSlot.LEGS),
    LEFT_FOOT(EquipmentSlot.FEET),
    RIGHT_FOOT(EquipmentSlot.FEET);

    public static final Codec<BodyPart> CODEC = Codec.STRING.xmap(BodyPart::valueOf, BodyPart::name);
    private final EquipmentSlot armorSlot;

    BodyPart(EquipmentSlot armorSlot) {
        this.armorSlot = armorSlot;
    }

    public EquipmentSlot armorSlot() {
        return armorSlot;
    }
}
