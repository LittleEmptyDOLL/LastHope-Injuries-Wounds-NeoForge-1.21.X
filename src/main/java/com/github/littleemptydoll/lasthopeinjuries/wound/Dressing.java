package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** An applied dressing: item identity, remaining cleanliness, and blood saturation (0–100). */
public record Dressing(String item, float cleanliness, float saturation) {
    public static final Codec<Dressing> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Dressing::item),
            Codec.FLOAT.fieldOf("cleanliness").forGetter(Dressing::cleanliness),
            Codec.FLOAT.fieldOf("saturation").forGetter(Dressing::saturation)
    ).apply(i, Dressing::new));

    public Dressing {
        cleanliness = Math.max(0, Math.min(100, cleanliness));
        saturation = Math.max(0, Math.min(100, saturation));
    }

    public static Dressing fresh(String item) {
        return new Dressing(item, 100, 0);
    }

    public static Dressing legacy() {
        return fresh("lasthopeinjuries:legacy_dressing");
    }

    public boolean freshEnough() {
        return cleanliness >= 99 && saturation < 1;
    }

    public float effectiveness(WoundType type) {
        if (item.equals("lasthopeinjuries:legacy_dressing") && saturation < 100) return 1.0F;
        if (type == WoundType.SCRATCH) return Math.min(1.0F, (100 - saturation) / 25.0F);
        if (type == WoundType.LACERATION) return Math.min(1.0F, (100 - saturation) / 50.0F);
        float base = switch (type) {
            case DEEP_LACERATION -> item.endsWith(":plaster") ? 0.45F : 0.95F;
            case PUNCTURE, BITE -> item.endsWith(":plaster") ? 0.70F : 0.90F;
            default -> 1.0F;
        };
        float remaining = 1.0F - saturation / 100.0F;
        // Compression wears off as the dressing soaks through, without losing most of its benefit early.
        return base * remaining;
    }

    public Dressing advance(double bloodFlow, int exposure) {
        // A level-five deep cut now takes several minutes to soak through one dressing.
        float nextSaturation = (float) Math.min(100, saturation + Math.max(0, bloodFlow) * 0.4);
        float nextCleanliness = Math.max(0, cleanliness - (0.02F + nextSaturation * 0.001F) * exposure);
        return new Dressing(item, nextCleanliness, nextSaturation);
    }
}
