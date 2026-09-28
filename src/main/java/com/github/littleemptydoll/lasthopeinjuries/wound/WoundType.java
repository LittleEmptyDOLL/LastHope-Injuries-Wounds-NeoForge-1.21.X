package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.serialization.Codec;

public enum WoundType {
    BRUISE(0, 0, 180),
    SCRATCH(2, 8, 240),
    LACERATION(5, 16, 420),
    DEEP_LACERATION(10, 25, 720),
    PUNCTURE(6, 22, 600),
    BURN(1, 12, 600),
    BITE(4, 35, 600);

    public static final Codec<WoundType> CODEC = Codec.STRING.xmap(WoundType::valueOf, WoundType::name);
    private final int bleed;
    private final int contamination;
    private final int healSeconds;

    WoundType(int bleed, int contamination, int healSeconds) {
        this.bleed = bleed;
        this.contamination = contamination;
        this.healSeconds = healSeconds;
    }

    public int bleed() { return bleed; }
    public int contamination() { return contamination; }
    public int healSeconds() { return healSeconds; }
}
