package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

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

    public WoundState advance() {
        List<Wound> next = wounds.stream().map(Wound::advance).filter(w -> !w.healed()).toList();
        double bleeding = next.stream().mapToDouble(Wound::bleedingPerSecond).sum();
        // A stopped bleed leaves the reserve depleted; blood then recovers slowly over time.
        float level = (float) (bloodLevel - bleeding + (bleeding == 0 ? 0.08 : 0));
        return new WoundState(next, level);
    }
}
