package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record WoundState(List<Wound> wounds, float bloodLoss) {
    public static final Codec<WoundState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Wound.CODEC.listOf().fieldOf("wounds").forGetter(WoundState::wounds),
            Codec.FLOAT.fieldOf("blood_loss").forGetter(WoundState::bloodLoss)
    ).apply(i, WoundState::new));

    public WoundState {
        wounds = List.copyOf(wounds);
        bloodLoss = Math.max(0, Math.min(100, bloodLoss));
    }

    public static WoundState empty() {
        return new WoundState(List.of(), 0);
    }

    public WoundState advance() {
        List<Wound> next = wounds.stream().map(Wound::advance).filter(w -> !w.healed()).toList();
        double bleeding = next.stream().mapToDouble(Wound::bleedingPerSecond).sum();
        // Recovery takes place only after bleeding stops.
        float loss = (float) Math.max(0, Math.min(100, bloodLoss + bleeding
                - (bleeding < 0.01 ? 0.08 : 0)));
        return new WoundState(next, loss);
    }
}
