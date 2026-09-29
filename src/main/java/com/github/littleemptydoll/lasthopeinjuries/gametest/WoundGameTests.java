package com.github.littleemptydoll.lasthopeinjuries.gametest;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.moodle.MoodleState;
import com.github.littleemptydoll.lasthopeinjuries.wound.BodyPart;
import com.github.littleemptydoll.lasthopeinjuries.wound.RecoveryModifier;
import com.github.littleemptydoll.lasthopeinjuries.wound.Wound;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundType;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LastHopeInjuries.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoundGameTests {
    private WoundGameTests() {}

    @GameTest(template = "empty")
    public static void woundStatePersistsAndBleeds(GameTestHelper helper) {
        Wound wound = Wound.create(BodyPart.LEFT_ARM, WoundType.LACERATION, 2);
        WoundState original = new WoundState(List.of(wound), 100);
        var encoded = WoundState.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        WoundState decoded = WoundState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        WoundState advanced = decoded.advance();
        WoundState stopped = new WoundState(List.of(wound.bandage()), advanced.bloodLevel()).advance();
        JsonObject legacy = encoded.getAsJsonObject();
        legacy.remove("blood_level");
        legacy.addProperty("blood_loss", 45);
        WoundState migrated = WoundState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        if (!decoded.equals(original) || advanced.wounds().size() != 1
                || advanced.bloodLevel() >= 100 || advanced.bleedingRate() <= 0
                || stopped.bleedingRate() != 0 || stopped.bloodLevel() <= advanced.bloodLevel()
                || migrated.bloodLevel() != 55
                || Wound.create(BodyPart.HEAD, WoundType.BURN, 3).bleedingPerSecond() != 0) {
            helper.fail("Wound state failed persistence or bleeding progression");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void dressingSaturatesAndLegacyWoundsLoad(GameTestHelper helper) {
        Wound deep = Wound.create(BodyPart.CHEST, WoundType.DEEP_LACERATION, 4);
        double openBleeding = deep.bleedingPerSecond();
        Wound bandaged = deep.bandage("legendarysurvivaloverhaul:bandage");
        double freshBleeding = bandaged.bleedingPerSecond();
        Wound soaked = bandaged;
        for (int second = 0; second < 100; second++) soaked = soaked.advance();
        Wound replaced = soaked.bandage("legendarysurvivaloverhaul:bandage");

        JsonObject oldWound = Wound.CODEC.encodeStart(JsonOps.INSTANCE, deep).getOrThrow().getAsJsonObject();
        oldWound.addProperty("bandaged", true);
        Wound migrated = Wound.CODEC.parse(JsonOps.INSTANCE, oldWound).getOrThrow();
        Wound roundTrip = Wound.CODEC.parse(JsonOps.INSTANCE,
                Wound.CODEC.encodeStart(JsonOps.INSTANCE, soaked).getOrThrow()).getOrThrow();

        if (Math.abs(freshBleeding - openBleeding * 0.05) > 0.0001
                || soaked.dressing() == null || soaked.dressing().saturation() <= 0
                || soaked.dressing().cleanliness() >= 100 || soaked.bleedingPerSecond() >= freshBleeding
                || replaced.bleedingPerSecond() >= soaked.bleedingPerSecond()
                || replaced.removeDressing().bleedingPerSecond()
                        != soaked.removeDressing().bleedingPerSecond()
                || !soaked.equals(roundTrip) || !migrated.bandaged()
                || migrated.bleedingPerSecond() != 0) {
            helper.fail("Dressing effectiveness, replacement, or legacy migration failed");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void deepCutDressingSlowsBloodLoss(GameTestHelper helper) {
        Wound deep = Wound.create(BodyPart.CHEST, WoundType.DEEP_LACERATION, 5);
        WoundState state = new WoundState(List.of(deep.bandage("legendarysurvivaloverhaul:bandage")), 100);
        for (int second = 0; second < 120; second++) state = state.advance();
        Wound remaining = state.wounds().getFirst();
        if (remaining.dressing().saturation() >= 30 || remaining.dressing().saturation() <= 0
                || state.bloodLevel() < 88 || remaining.bleedingPerSecond() >= deep.bleedingPerSecond()) {
            helper.fail("One bandaged deep cut exhausted the blood reserve too quickly");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void wetExposureAndDirtyDressingRequireCleaning(GameTestHelper helper) {
        Wound deep = Wound.create(BodyPart.LEFT_LEG, WoundType.DEEP_LACERATION, 2);
        Wound dry = deep;
        Wound submerged = deep;
        Wound covered = deep.bandage();
        for (int second = 0; second < 180; second++) {
            dry = dry.advance(1);
            submerged = submerged.advance(4);
            covered = covered.advance(4);
        }
        Wound dirtyDressing = new Wound(covered.id(), covered.part(), covered.type(), covered.severity(),
                covered.age(), new com.github.littleemptydoll.lasthopeinjuries.wound.Dressing(
                        "legendarysurvivaloverhaul:bandage", 20, covered.dressing().saturation()),
                covered.contamination(), covered.infection(), covered.healingProgress(),
                covered.clotting(), covered.sutured(), covered.herbSeconds());
        Wound exposed = dirtyDressing.removeDressing();
        if (submerged.contamination() <= dry.contamination()
                || covered.contamination() != deep.contamination()
                || covered.dressing().cleanliness() >= 100
                || dirtyDressing.canBandage() || exposed.canBandage()
                || exposed.contamination() < 40 || !exposed.clean().canBandage()) {
            helper.fail("Exposure, dressing protection, or cleaning requirement failed");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void infectionRiskAndAntibioticsAreSeparateFromCleaning(GameTestHelper helper) {
        Wound bite = Wound.create(BodyPart.RIGHT_ARM, WoundType.BITE, 3);
        Wound unlucky = bite;
        Wound lucky = bite;
        for (int second = 0; second < 180; second++) {
            unlucky = unlucky.advance(4, 0.0);
            lucky = lucky.advance(4, 1.0);
        }
        Wound established = new Wound(bite.id(), bite.part(), bite.type(), bite.severity(),
                0, null, 80, 82, 0, 0, false, 0);
        Wound washed = established.clean();
        Wound treated = washed.antibiotics();
        if (unlucky.infection() <= 0 || lucky.infection() != 0
                || washed.contamination() != 0 || washed.infection() != 82
                || treated.infection() != 42 || treated.contamination() != 0
                || treated.antibiotics().infection() != 2) {
            helper.fail("Infection chance or antibiotic treatment was not independent of washing");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void healingRequiresCareAndPersists(GameTestHelper helper) {
        Wound cut = Wound.create(BodyPart.LEFT_ARM, WoundType.LACERATION, 2);
        Wound untreated = cut;
        Wound treated = cut.bandage();
        for (int second = 0; second < 150; second++) {
            untreated = untreated.advance(1, 1.0);
            treated = treated.advance(1, 1.0);
        }
        Wound infected = new Wound(treated.id(), treated.part(), treated.type(), treated.severity(),
                treated.age(), treated.dressing(), treated.contamination(), 60, treated.healingProgress(),
                treated.clotting(), treated.sutured(), treated.herbSeconds());
        Wound infectedNext = infected.advance(1, 1.0);
        Wound decoded = Wound.CODEC.parse(JsonOps.INSTANCE,
                Wound.CODEC.encodeStart(JsonOps.INSTANCE, treated).getOrThrow()).getOrThrow();
        JsonObject legacy = Wound.CODEC.encodeStart(JsonOps.INSTANCE, treated).getOrThrow().getAsJsonObject();
        legacy.remove("healing_progress");
        Wound migrated = Wound.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        if (untreated.healingProgress() <= 0 || treated.healingProgress() <= untreated.healingProgress()
                || infectedNext.healingProgress() != infected.healingProgress()
                || !treated.equals(decoded) || migrated.healingProgress() <= 0) {
            helper.fail("Healing progression, stabilization, or legacy migration failed");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void healedWoundEventuallyLeavesState(GameTestHelper helper) {
        Wound bruise = Wound.create(BodyPart.RIGHT_LEG, WoundType.BRUISE, 1);
        WoundState state = new WoundState(List.of(bruise), 100);
        for (int second = 0; second < 200; second++) state = state.advance(1, () -> 1.0);
        if (!state.wounds().isEmpty()) helper.fail("A stable bruise did not heal");
        else helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moodlesSeparateBleedingAndPriorBloodLoss(GameTestHelper helper) {
        Wound wound = Wound.create(BodyPart.LEFT_ARM, WoundType.LACERATION, 3).bandage();
        MoodleState moodles = MoodleState.from(new WoundState(List.of(wound), 55));
        if (moodles.bleeding() != 0 || moodles.bloodLoss() != 1
                || moodles.pain() == 0 || moodles.sickness() != 0) {
            helper.fail("A closed bleed erased the remaining blood loss or pain");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void moodlesCombineSmallWoundsAndTrackSevereInfection(GameTestHelper helper) {
        Wound a = Wound.create(BodyPart.LEFT_ARM, WoundType.SCRATCH, 1);
        Wound b = Wound.create(BodyPart.RIGHT_ARM, WoundType.SCRATCH, 1);
        Wound c = Wound.create(BodyPart.LEFT_LEG, WoundType.SCRATCH, 1);
        MoodleState small = MoodleState.from(new WoundState(List.of(a, b, c), 29));
        Wound infected = new Wound(a.id(), a.part(), a.type(), a.severity(), a.age(),
                a.dressing(), a.contamination(), 85, a.healingProgress(),
                a.clotting(), a.sutured(), a.herbSeconds());
        MoodleState sick = MoodleState.from(new WoundState(List.of(infected), 100));
        if (small.pain() != 2 || small.bloodLoss() != 3
                || Math.abs(small.outgoingDamageMultiplier() - 0.54F) > 0.001F
                || sick.sickness() != 3 || MoodleState.from(WoundState.empty()).pain() != 0) {
            helper.fail("Pain aggregation, blood loss penalty, or infection moodle failed");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void lightWoundsClotNaturallyAndDeepCutsRemainDangerous(GameTestHelper helper) {
        Wound scratch = Wound.create(BodyPart.LEFT_ARM, WoundType.SCRATCH, 5);
        Wound deep = Wound.create(BodyPart.CHEST, WoundType.DEEP_LACERATION, 5);
        WoundState shallowState = new WoundState(List.of(scratch), 100);
        WoundState deepState = new WoundState(List.of(deep), 100);
        for (int second = 0; second < 180; second++) {
            shallowState = shallowState.advance(1, () -> 1.0);
            deepState = deepState.advance(1, () -> 1.0);
        }
        if (shallowState.bleedingRate() != 0 || shallowState.bloodLevel() < 98
                || deepState.bleedingRate() <= 0 || deepState.bloodLevel() <= 40
                || deepState.wounds().getFirst().clotting() < 0.85F) {
            helper.fail("Natural clotting did not make light wounds survivable while retaining deep-cut risk");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void medkitHerbsSuturesAndRecoveryHaveDistinctRoles(GameTestHelper helper) {
        Wound deep = Wound.create(BodyPart.CHEST, WoundType.DEEP_LACERATION, 3);
        Wound aid = deep.medkit("legendarysurvivaloverhaul:medkit");
        Wound stitched = deep.clean().suture();
        Wound infected = new Wound(deep.id(), deep.part(), deep.type(), deep.severity(),
                deep.age(), null, 80, 60, 0, 0, false, 0).medkit("legendarysurvivaloverhaul:medkit");
        Wound herb = aid.herbs();
        Wound normal = aid;
        for (int second = 0; second < 120; second++) {
            herb = herb.advance(1, 1.0, 1);
            normal = normal.advance(1, 1.0, 1);
        }
        double healthy = RecoveryModifier.calculate(20, 20, 0, 0, false, 0);
        double poor = RecoveryModifier.calculate(0, 0, 2, 100000, false, 3);
        MoodleState pain = MoodleState.from(new WoundState(List.of(deep), 100));
        MoodleState morphine = MoodleState.from(new WoundState(List.of(deep), 100), true);
        if (aid.contamination() != 0 || aid.sutured() || !aid.stabilized()
                || deep.canSuture() || !deep.clean().canSuture() || infected.infection() != 60
                || !stitched.sutured() || stitched.bleedingPerSecond() >= deep.bleedingPerSecond()
                || herb.healingProgress() <= normal.healingProgress()
                || herb.herbSeconds() != 480 || healthy <= 1 || poor < 0.25 || poor >= 1
                || morphine.pain() >= pain.pain()
                || !Wound.CODEC.parse(JsonOps.INSTANCE,
                        Wound.CODEC.encodeStart(JsonOps.INSTANCE, herb).getOrThrow()).getOrThrow().equals(herb)) {
            helper.fail("Treatment and recovery effects did not remain distinct or persist");
        } else {
            helper.succeed();
        }
    }
}
