package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable so each change can be persisted with Entity#setData. Age is measured in seconds. */
public record Wound(UUID id, BodyPart part, WoundType type, int severity, int age,
                    Dressing dressing, int contamination, int infection, float healingProgress,
                    float clotting, boolean sutured, int sutureIntegrity, int herbSeconds) {
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
            Codec.FLOAT.optionalFieldOf("healing_progress", -1.0F).forGetter(Wound::healingProgress),
            Codec.FLOAT.optionalFieldOf("clotting", -1.0F).forGetter(Wound::clotting),
            Codec.BOOL.optionalFieldOf("sutured", false).forGetter(Wound::sutured),
            Codec.INT.optionalFieldOf("suture_integrity", -1).forGetter(Wound::sutureIntegrity),
            Codec.INT.optionalFieldOf("herb_seconds", 0).forGetter(Wound::herbSeconds)
    ).apply(i, (id, part, type, severity, age, oldBandaged, dressing, contamination, infection,
                 progress, clotting, sutured, integrity, herbSeconds) ->
            new Wound(id, part, type, severity, age,
                    dressing.orElseGet(() -> oldBandaged ? Dressing.legacy() : null), contamination, infection,
                    // Approximate existing worlds' age-based healing when the progress field is absent.
                    progress >= 0 ? progress : Math.min(99.9F,
                            age * 100.0F / (type.healSeconds() * severity)),
                    clotting >= 0 ? clotting : Math.min(maxClotting(type),
                            age * maxClotting(type) / clotSeconds(type)), sutured,
                    integrity >= 0 ? integrity : sutured ? 100 : 0, herbSeconds)));

    public Wound {
        healingProgress = Float.isFinite(healingProgress)
                ? Math.max(0, Math.min(100, healingProgress)) : 0;
        clotting = Float.isFinite(clotting) ? Math.max(0, Math.min(1, clotting)) : 0;
        sutureIntegrity = Math.max(0, Math.min(100, sutureIntegrity));
        sutured = sutured && sutureIntegrity > 0;
        herbSeconds = Math.max(0, herbSeconds);
    }

    /** Compatibility for callers constructing wounds before suture integrity was persisted. */
    public Wound(UUID id, BodyPart part, WoundType type, int severity, int age,
                 Dressing dressing, int contamination, int infection, float healingProgress,
                 float clotting, boolean sutured, int herbSeconds) {
        this(id, part, type, severity, age, dressing, contamination, infection, healingProgress,
                clotting, sutured, sutured ? 100 : 0, herbSeconds);
    }

    public static Wound create(BodyPart part, WoundType type, int severity) {
        return new Wound(UUID.randomUUID(), part, type, Math.max(1, Math.min(5, severity)),
                0, null, type.contamination(), 0, 0, 0, false, 0, 0);
    }

    public Wound bandage() {
        return bandage("lasthopeinjuries:field_dressing");
    }

    public Wound bandage(String item) {
        return new Wound(id, part, type, severity, age, Dressing.fresh(item), contamination, infection,
                healingProgress, clotting, sutured, sutureIntegrity, herbSeconds);
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
        return new Wound(id, part, type, severity, age, null, exposedDirt, infection, healingProgress,
                clotting, sutured, sutureIntegrity, herbSeconds);
    }

    public Wound clean() {
        // Washing removes dirt and future exposure risk, but established infection needs medicine.
        return new Wound(id, part, type, severity, age, dressing, 0, infection, healingProgress,
                clotting, sutured, sutureIntegrity, herbSeconds);
    }

    public Wound antibiotics() {
        return new Wound(id, part, type, severity, age, dressing, contamination,
                Math.max(0, infection - 40), healingProgress, clotting, sutured, sutureIntegrity, herbSeconds);
    }

    public Wound medkit(String item) {
        // Emergency care cleans and compresses a wound; it does not replace antibiotics or stitches.
        return new Wound(id, part, type, severity, age, Dressing.fresh(item), 0,
                infection < 20 ? Math.max(0, infection - 10) : infection, healingProgress,
                Math.max(clotting, 0.8F), sutured, sutureIntegrity, herbSeconds);
    }

    public Wound suture() {
        return new Wound(id, part, type, severity, age, dressing, contamination, infection,
                healingProgress, Math.max(clotting, 0.9F), true, 100, herbSeconds);
    }

    public Wound herbs() {
        return new Wound(id, part, type, severity, age, dressing, contamination, infection,
                healingProgress, clotting, sutured, sutureIntegrity, 600);
    }

    public boolean canSuture() {
        return type == WoundType.DEEP_LACERATION && sutureIntegrity < 70
                && dressing == null && contamination < 20;
    }

    public boolean canMedkit() {
        return contamination > 0 || clotting < 0.8F || dressing == null
                || !dressing.freshEnough() || (infection > 0 && infection < 20);
    }

    public boolean canUseHerbs() {
        return stabilized() && infection < 50 && herbSeconds <= 300 && healingProgress < 100;
    }

    public boolean stabilized() {
        return type.bleed() == 0 || bleedingFraction(clotting, dressing) <= 0.25;
    }

    public Wound advance() {
        return advance(1);
    }

    /** Exposure is 1 in dry conditions, 2 in rain and 4 when submerged. */
    public Wound advance(int exposure) {
        return advance(exposure, ThreadLocalRandom.current().nextDouble());
    }

    public Wound advance(int exposure, double infectionRoll) {
        return advance(exposure, infectionRoll, 1.0);
    }

    public Wound advance(int exposure, double infectionRoll, double recoveryModifier) {
        int nextContamination = contamination;
        float targetClot = sutured ? 1.0F : maxClotting(type);
        float nextClotting = Math.min(targetClot, clotting
                + targetClot * (dressing == null ? 1 : 4) / clotSeconds(type));
        Dressing nextDressing = dressing == null ? null
                : dressing.advance(baseBleedingPerSecond() * (1 - clotting), exposure);
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
        double bleedingFraction = type.bleed() == 0 ? 0 : bleedingFraction(nextClotting, nextDressing);
        float nextHealing = healingProgress;
        // Healing begins only after bleeding is controlled and established infection is treated.
        if (bleedingFraction <= 0.25 && nextInfection < 50) {
            double speed = 1 - bleedingFraction * 0.8;
            if (nextInfection >= 20) speed *= 0.4;
            if (nextContamination >= 40) speed *= 0.5;
            if (nextDressing != null && nextDressing.cleanliness() < 40) speed *= 0.5;
            if (herbSeconds > 0) speed *= 1.35;
            speed *= Double.isFinite(recoveryModifier) ? Math.max(0.25, Math.min(2, recoveryModifier)) : 1;
            nextHealing = (float) Math.min(100, healingProgress
                    + 100.0 * speed / (type.healSeconds() * severity));
        }
        return new Wound(id, part, type, severity, age + 1, nextDressing,
                nextContamination, nextInfection, nextHealing, nextClotting, sutured,
                sutureIntegrity, Math.max(0, herbSeconds - 1));
    }

    /** A hit to this region may reopen a healing wound without raising its original severity. */
    public Wound aggravate(float healthDamage) {
        float lostHealing = Math.min(25, healthDamage * 2.5F);
        int nextIntegrity = sutured ? Math.max(0, sutureIntegrity
                - Math.min(60, Math.round(10 + healthDamage * 7))) : 0;
        float lostClotting = Math.min(0.25F, healthDamage * 0.03F);
        return new Wound(id, part, type, severity, age, dressing, contamination, infection,
                Math.max(0, healingProgress - lostHealing), Math.max(0, clotting - lostClotting),
                nextIntegrity > 0, nextIntegrity, herbSeconds);
    }

    public double bleedingPerSecond() {
        return baseBleedingPerSecond() * bleedingFraction(clotting, dressing);
    }

    private double bleedingFraction(float clot, Dressing applied) {
        return (1 - clot) * (applied == null ? 1 : 1 - applied.effectiveness(type));
    }

    private static float maxClotting(WoundType type) {
        return switch (type) {
            case DEEP_LACERATION -> 0.9F;
            case PUNCTURE, BITE -> 0.95F;
            default -> 1.0F;
        };
    }

    private static int clotSeconds(WoundType type) {
        return switch (type) {
            case SCRATCH -> 30;
            case LACERATION -> 90;
            case DEEP_LACERATION -> 180;
            case PUNCTURE -> 120;
            case BITE -> 150;
            case BRUISE, BURN -> 1;
        };
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
