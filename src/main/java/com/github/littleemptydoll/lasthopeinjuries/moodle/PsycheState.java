package com.github.littleemptydoll.lasthopeinjuries.moodle;

/** Short-lived stress and panic; unlike physical wounds, this state fades after danger passes. */
public record PsycheState(double stress, double panic) {
    public static final PsycheState CALM = new PsycheState(0, 0);

    public PsycheState advance(MoodleState medical, int nearbyHostiles, boolean resting) {
        int threats = Math.min(12, Math.max(0, nearbyHostiles));
        double injury = Math.max(0, medical.pain() - 1) * 7
                + medical.bloodLoss() * 6 + medical.bleeding() * 3 + medical.sickness() * 5;
        double danger = Math.max(0, threats - 1) * 7;
        double targetStress = Math.min(100, Math.max(injury, danger)
                + Math.min(12, injury * threats * 0.05));
        double newStress = targetStress > stress
                ? Math.min(targetStress, stress + Math.max(1, (targetStress - stress) * 0.20))
                : Math.max(targetStress, stress - (resting && threats == 0 ? 4 : 2));
        // A crowd can cause panic; a recent blow can keep panic high briefly after it disperses.
        double targetPanic = threats >= 6 ? Math.min(90, (threats - 5) * 12) : 0;
        double newPanic = targetPanic > panic ? Math.min(targetPanic, panic + 8)
                : Math.max(targetPanic, panic - (resting && threats == 0 ? 7 : 4));
        return new PsycheState(newStress, newPanic);
    }

    public PsycheState hit(float healthDamage) {
        if (healthDamage < 4) return this;
        return new PsycheState(Math.min(100, stress + Math.min(15, healthDamage * 1.5)),
                Math.min(100, panic + Math.min(36, healthDamage * 4)));
    }

    public int stressLevel() { return level(stress); }
    public int panicLevel() { return level(panic); }

    public static float damageMultiplier(int panicLevel) {
        return 1.0F - Math.max(0, Math.min(4, panicLevel)) * 0.04F;
    }

    private static int level(double value) {
        return value < 25 ? 0 : value < 45 ? 1 : value < 65 ? 2 : value < 85 ? 3 : 4;
    }
}
