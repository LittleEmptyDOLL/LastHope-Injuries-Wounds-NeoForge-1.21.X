package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.ArrayList;
import java.util.function.DoubleSupplier;

/** Blood level is stored; the current bleeding rate is derived from active wounds. */
public record WoundState(List<Wound> wounds, float bloodLevel) {
    public static final Codec<WoundState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Wound.CODEC.listOf().fieldOf("wounds").forGetter(WoundState::wounds),
            Codec.FLOAT.optionalFieldOf("blood_level", -1.0F).forGetter(WoundState::bloodLevel),
            // Previous releases persisted depletion as blood_loss. Read it, but write only blood_level.
            Codec.FLOAT.optionalFieldOf("blood_loss", -1.0F).forGetter(state -> -1.0F)
    ).apply(i, (wounds, level, oldLoss) -> new WoundState(wounds,
            level >= 0 ? level : oldLoss >= 0 ? 100 - oldLoss : 100)));

    public WoundState {
        wounds = List.copyOf(wounds);
        bloodLevel = Math.max(0, Math.min(100, bloodLevel));
    }

    public static WoundState empty() {
        return new WoundState(List.of(), 100);
    }

    public float bleedingRate() {
        return (float) wounds.stream().mapToDouble(Wound::bleedingPerSecond).sum();
    }

    /** Returns this state when the hit did not affect an existing, partially stabilized wound. */
    public WoundState aggravate(BodyPart part, float healthDamage, double roll) {
        if (healthDamage < 2 || roll >= Math.min(0.65, 0.10 + healthDamage * 0.05)) return this;
        int target = -1;
        int risk = -1;
        for (int i = 0; i < wounds.size(); i++) {
            Wound wound = wounds.get(i);
            if (wound.part() != part || wound.healed() || wound.type().bleed() == 0
                    || wound.healingProgress() < 5 && wound.clotting() < 0.4F && !wound.sutured()) continue;
            int woundRisk = wound.type().bleed() * wound.severity();
            if (woundRisk > risk) { target = i; risk = woundRisk; }
        }
        if (target < 0) return this;
        List<Wound> changed = new ArrayList<>(wounds);
        changed.set(target, wounds.get(target).aggravate(healthDamage));
        return new WoundState(changed, bloodLevel);
    }

    public WoundState advance() {
        return advance(1);
    }

    public WoundState advance(int exposure) {
        return advance(exposure, Math::random);
    }

    public WoundState advance(int exposure, DoubleSupplier infectionRoll) {
        return advance(exposure, infectionRoll, 1);
    }

    public WoundState advance(int exposure, DoubleSupplier infectionRoll, double recoveryModifier) {
        List<Wound> next = wounds.stream().map(w -> w.advance(exposure,
                        infectionRoll.getAsDouble(), recoveryModifier))
                .filter(w -> !w.healed()).toList();
        double bleeding = next.stream().mapToDouble(Wound::bleedingPerSecond).sum();
        // A stopped bleed leaves the reserve depleted; blood then recovers slowly over time.
        float level = (float) (bloodLevel - bleeding + (bleeding == 0 ? 0.08 * recoveryModifier : 0));
        return new WoundState(next, level);
    }
}
