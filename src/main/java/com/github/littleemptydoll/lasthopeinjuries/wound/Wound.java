package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;

/** Immutable so each change can be persisted with Entity#setData. Age is measured in seconds. */
public record Wound(UUID id, BodyPart part, WoundType type, int severity, int age,
                    boolean bandaged, int contamination, int infection) {
    public static final Codec<Wound> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(Wound::id),
            BodyPart.CODEC.fieldOf("part").forGetter(Wound::part),
            WoundType.CODEC.fieldOf("type").forGetter(Wound::type),
            Codec.intRange(1, 5).fieldOf("severity").forGetter(Wound::severity),
            Codec.INT.fieldOf("age").forGetter(Wound::age),
            Codec.BOOL.fieldOf("bandaged").forGetter(Wound::bandaged),
            Codec.intRange(0, 100).fieldOf("contamination").forGetter(Wound::contamination),
            Codec.intRange(0, 100).fieldOf("infection").forGetter(Wound::infection)
    ).apply(i, Wound::new));

    public static Wound create(BodyPart part, WoundType type, int severity) {
        return new Wound(UUID.randomUUID(), part, type, Math.max(1, Math.min(5, severity)),
                0, false, type.contamination(), 0);
    }

    public Wound bandage() {
        return new Wound(id, part, type, severity, age, true, contamination, infection);
    }

    public Wound clean() {
        return new Wound(id, part, type, severity, age, bandaged, 0, Math.max(0, infection - 20));
    }

    public Wound advance() {
        int nextContamination = contamination;
        // An uncovered wound may become dirty again; a clean bandage protects it.
        if (!bandaged && age % 30 == 0 && type != WoundType.BRUISE) {
            nextContamination = Math.min(100, nextContamination + 1);
        }
        int nextInfection = infection;
        if (nextContamination >= 40 && age % 15 == 0) nextInfection = Math.min(100, infection + 1);
        if (nextContamination < 20 && age % 30 == 0) nextInfection = Math.max(0, infection - 1);
        return new Wound(id, part, type, severity, age + 1, bandaged,
                nextContamination, nextInfection);
    }

    public double bleedingPerSecond() {
        return bandaged ? 0 : type.bleed() * severity / 100.0;
    }

    public boolean healed() {
        return infection < 40 && age >= type.healSeconds() * severity;
    }
}
