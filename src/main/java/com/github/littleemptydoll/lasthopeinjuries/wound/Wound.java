package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable so each change can be persisted with Entity#setData. Age is measured in seconds. */
public record Wound(UUID id, BodyPart part, WoundType type, int severity, int age,
                    Dressing dressing, int contamination, int infection, float healingProgress) {
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
            Codec.intRange(0, 100).fieldOf("infection").forGetter(Wound::infection),
            Codec.FLOAT.optionalFieldOf("healing_progress", -1.0F).forGetter(Wound::healingProgress)
    ).apply(i, (id, part, type, severity, age, oldBandaged, dressing, contamination, infection, progress) ->
            new Wound(id, part, type, severity, age,
                    dressing.orElseGet(() -> oldBandaged ? Dressing.legacy() : null), contamination, infection,
                    // Approximate existing worlds' age-based healing when the progress field is absent.
                    progress >= 0 ? progress : Math.min(99.9F,
                            age * 100.0F / (type.healSeconds() * severity)))));

    public Wound {
        healingProgress = Float.isFinite(healingProgress)
                ? Math.max(0, Math.min(100, healingProgress)) : 0;
    }

    public static Wound create(BodyPart part, WoundType type, int severity) {
        return new Wound(UUID.randomUUID(), part, type, Math.max(1, Math.min(5, severity)),
                0, null, type.contamination(), 0, 0);
    }

    public Wound bandage() {
        return bandage("lasthopeinjuries:field_dressing");
    }

    public Wound bandage(String item) {
        return new Wound(id, part, type, severity, age, Dressing.fresh(item), contamination, infection,
                healingProgress);
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
        return new Wound(id, part, type, severity, age, null, exposedDirt, infection, healingProgress);
    }

    public Wound clean() {
        // Washing removes dirt and future exposure risk, but established infection needs medicine.
        return new Wound(id, part, type, severity, age, dressing, 0, infection, healingProgress);
    }

    public Wound antibiotics() {
        return new Wound(id, part, type, severity, age, dressing, contamination,
                Math.max(0, infection - 40), healingProgress);
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
        double bleedingFraction = type.bleed() == 0 ? 0
                : nextDressing == null ? 1 : 1 - nextDressing.effectiveness(type);
        float nextHealing = healingProgress;
        // Healing begins only after bleeding is controlled and established infection is treated.
        if (bleedingFraction <= 0.25 && nextInfection < 50) {
            double speed = 1 - bleedingFraction * 0.8;
            if (nextInfection >= 20) speed *= 0.4;
            if (nextContamination >= 40) speed *= 0.5;
            if (nextDressing != null && nextDressing.cleanliness() < 40) speed *= 0.5;
            nextHealing = (float) Math.min(100, healingProgress
                    + 100.0 * speed / (type.healSeconds() * severity));
        }
        return new Wound(id, part, type, severity, age + 1, nextDressing,
                nextContamination, nextInfection, nextHealing);
    }

    public double bleedingPerSecond() {
        return baseBleedingPerSecond() * (dressing == null ? 1 : 1 - dressing.effectiveness(type));
    }

    private double baseBleedingPerSecond() {
        return type.bleed() * effectiveSeverity() / 100.0;
    }

    public int effectiveSeverity() {
        // The original severity keeps the healing duration stable as the wound improves.
        return Math.max(1, severity - (int) (healingProgress / 25));
    }

    public boolean healed() {
        return healingProgress >= 100;
    }
}
