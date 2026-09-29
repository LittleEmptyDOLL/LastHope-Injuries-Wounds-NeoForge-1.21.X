package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable so each change can be persisted with Entity#setData. Age is measured in seconds. */
public record Wound(UUID id, BodyPart part, WoundType type, int severity, int age,
                    Dressing dressing, int contamination, int infection) {
    public static final Codec<Wound> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(Wound::id),
            BodyPart.CODEC.fieldOf("part").forGetter(Wound::part),
            WoundType.CODEC.fieldOf("type").forGetter(Wound::type),
            Codec.intRange(1, 5).fieldOf("severity").forGetter(Wound::severity),
            Codec.INT.fieldOf("age").forGetter(Wound::age),
            // Existing worlds stored a boolean. New worlds write the dressing object instead.
            Codec.BOOL.optionalFieldOf("bandaged", false).forGetter(wound -> false),
            Dressing.CODEC.optionalFieldOf("dressing").forGetter(wound -> Optional.ofNullable(wound.dressing())),
            Codec.intRange(0, 100).fieldOf("contamination").forGetter(Wound::contamination),
            Codec.intRange(0, 100).fieldOf("infection").forGetter(Wound::infection)
    ).apply(i, (id, part, type, severity, age, oldBandaged, dressing, contamination, infection) ->
            new Wound(id, part, type, severity, age,
                    dressing.orElseGet(() -> oldBandaged ? Dressing.legacy() : null), contamination, infection)));

    public static Wound create(BodyPart part, WoundType type, int severity) {
        return new Wound(UUID.randomUUID(), part, type, Math.max(1, Math.min(5, severity)),
                0, null, type.contamination(), 0);
    }

    public Wound bandage() {
        return bandage("lasthopeinjuries:field_dressing");
    }

    public Wound bandage(String item) {
        return new Wound(id, part, type, severity, age, Dressing.fresh(item), contamination, infection);
    }

    public boolean canBandage() {
        return contamination < 40 && (dressing == null ||
                (!dressing.freshEnough() && dressing.cleanliness() >= 40));
    }

    public boolean bandaged() {
        return dressing != null;
    }

    public Wound removeDressing() {
        int exposedDirt = dressing != null && dressing.cleanliness() < 40
                ? Math.max(contamination, 40) : contamination;
        return new Wound(id, part, type, severity, age, null, exposedDirt, infection);
    }

    public Wound clean() {
        // Washing removes dirt and future exposure risk, but established infection needs medicine.
        return new Wound(id, part, type, severity, age, dressing, 0, infection);
    }

    public Wound antibiotics() {
        return new Wound(id, part, type, severity, age, dressing, contamination,
                Math.max(0, infection - 40));
    }

    public Wound advance() {
        return advance(1);
    }

    /** Exposure is 1 in dry conditions, 2 in rain and 4 when submerged. */
    public Wound advance(int exposure) {
        return advance(exposure, ThreadLocalRandom.current().nextDouble());
    }

    public Wound advance(int exposure, double infectionRoll) {
        int nextContamination = contamination;
        Dressing nextDressing = dressing == null ? null : dressing.advance(baseBleedingPerSecond(), exposure);
        // Clean dressings protect the wound; saturated or dirty ones need replacing.
        if ((age + 1) % 30 == 0 && type != WoundType.BRUISE) {
            if (nextDressing == null || nextDressing.saturation() >= 100) {
                nextContamination = Math.min(100, nextContamination + exposure
                        + (type == WoundType.BITE || type == WoundType.PUNCTURE ? 1 : 0));
            } else if (nextDressing.cleanliness() < 40) {
                nextContamination = Math.min(100, nextContamination + 2 * exposure);
            }
        }
        int nextInfection = infection;
        if ((age + 1) % 30 == 0) {
            // Dirty wounds can inflame, but infection is a separate, non-guaranteed progression.
            double risk = Math.max(0, nextContamination - 30) / 200.0;
            if (type == WoundType.BITE) risk *= 1.5;
            else if (type == WoundType.PUNCTURE) risk *= 1.25;
            if (infectionRoll < risk) nextInfection = Math.min(100, infection + 1 + nextContamination / 25);
            else if (nextContamination < 20 && infection < 50) nextInfection = Math.max(0, infection - 1);
        }
        return new Wound(id, part, type, severity, age + 1, nextDressing,
                nextContamination, nextInfection);
    }

    public double bleedingPerSecond() {
        return baseBleedingPerSecond() * (dressing == null ? 1 : 1 - dressing.effectiveness(type));
    }

    private double baseBleedingPerSecond() {
        return type.bleed() * severity / 100.0;
    }

    public boolean healed() {
        return infection < 50 && age >= type.healSeconds() * severity;
    }
}
