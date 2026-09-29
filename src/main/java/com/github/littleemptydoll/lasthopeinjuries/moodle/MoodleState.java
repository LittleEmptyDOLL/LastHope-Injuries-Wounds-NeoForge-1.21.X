package com.github.littleemptydoll.lasthopeinjuries.moodle;

import com.github.littleemptydoll.lasthopeinjuries.wound.BodyPart;
import com.github.littleemptydoll.lasthopeinjuries.wound.Wound;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;

/** Derived from the synced wound state on both sides; these levels never need separate persistence. */
public record MoodleState(int bleeding, int bloodLoss, int pain, int sickness) {
    public static MoodleState from(WoundState state) {
        return from(state, false);
    }

    public static MoodleState from(WoundState state, boolean painSuppressed) {
        double rate = state.bleedingRate();
        int bleeding = rate < 0.01 ? 0 : rate < 0.10 ? 1 : rate < 0.30 ? 2 : rate < 0.60 ? 3 : 4;
        float blood = state.bloodLevel();
        int bloodLoss = blood >= 75 ? 0 : blood >= 50 ? 1 : blood >= 30 ? 2 : blood >= 15 ? 3 : 4;

        double painScore = 0;
        double infectionScore = 0;
        int infectedWounds = 0;
        for (Wound wound : state.wounds()) {
            double basePain = switch (wound.type()) {
                case BRUISE -> 0.6;
                case SCRATCH -> 0.3;
                case LACERATION -> 1.2;
                case DEEP_LACERATION, BURN -> 2.5;
                case PUNCTURE, BITE -> 2.0;
            };
            double severity = 0.5 + wound.effectiveSeverity() * 0.25;
            double infection = 1 + wound.infection() / 100.0 * 0.8;
            double location = wound.part() == BodyPart.HEAD || wound.part() == BodyPart.CHEST ? 1.2 : 1;
            double care = wound.dressing() != null && wound.dressing().cleanliness() >= 40
                    ? wound.dressing().item().endsWith(":medkit") ? 0.65 : 0.85 : 1;
            painScore += basePain * severity * infection * location * care
                    * (1 - wound.healingProgress() / 200.0);
            // Inflammation adds pain; sickness begins only at established bacterial infection.
            if (wound.infection() >= 50) {
                infectionScore = Math.max(infectionScore, wound.infection());
                infectedWounds++;
            }
        }
        if (painSuppressed) painScore *= 0.2;
        int pain = painScore < 0.2 ? 0 : painScore < 0.6 ? 1 : painScore < 3 ? 2
                : painScore < 6 ? 3 : 4;
        infectionScore += Math.max(0, infectedWounds - 1) * 10;
        int sickness = infectionScore < 50 ? 0 : infectionScore < 65 ? 1
                : infectionScore < 80 ? 2 : infectionScore < 95 ? 3 : 4;
        return new MoodleState(bleeding, bloodLoss, pain, sickness);
    }

    public int level(MoodleType type) {
        return switch (type) {
            case BLEEDING -> bleeding;
            case BLOOD_LOSS -> bloodLoss;
            case PAIN -> pain;
            case SICKNESS -> sickness;
        };
    }

    public float outgoingDamageMultiplier() {
        // Existing blood-loss damage penalty now follows the displayed level; pain adds its own cost.
        float bloodFactor = switch (bloodLoss) {
            case 0, 1 -> 1.0F;
            case 2 -> 0.8F;
            case 3, 4 -> 0.6F;
            default -> 1.0F;
        };
        float painFactor = switch (pain) {
            case 0, 1 -> 1.0F;
            case 2 -> 0.9F;
            case 3 -> 0.8F;
            case 4 -> 0.7F;
            default -> 1.0F;
        };
        return bloodFactor * painFactor;
    }
}
