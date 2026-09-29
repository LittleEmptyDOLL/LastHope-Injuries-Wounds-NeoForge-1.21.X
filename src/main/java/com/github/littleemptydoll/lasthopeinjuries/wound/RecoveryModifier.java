package com.github.littleemptydoll.lasthopeinjuries.wound;

/** Gentle recovery modifiers; existing LSO thirst and temperature damage remain LSO's responsibility. */
public final class RecoveryModifier {
    private RecoveryModifier() {}

    public static double calculate(int food, int hydration, int temperatureSeverity,
                                   int ticksSinceRest, boolean sleeping, int sickness) {
        double nutrition = food >= 18 ? 1.1 : food >= 12 ? 1 : food >= 7 ? 0.9
                : food >= 3 ? 0.75 : 0.4;
        double water = hydration >= 16 ? 1 : hydration >= 10 ? 0.9
                : hydration >= 5 ? 0.7 : 0.3;
        double temperature = temperatureSeverity >= 2 ? 0.6
                : temperatureSeverity == 1 ? 0.85 : 1;
        double rest = sleeping ? 1.2 : ticksSinceRest < 24000 ? 1.15
                : ticksSinceRest > 72000 ? 0.85 : 1;
        double illness = sickness >= 3 ? 0.5 : sickness >= 2 ? 0.8
                : sickness >= 1 ? 0.9 : 1;
        // Poor conditions slow natural recovery without making minor wounds inevitably fatal.
        return Math.max(0.25, Math.min(1.6, nutrition * water * temperature * rest * illness));
    }
}
