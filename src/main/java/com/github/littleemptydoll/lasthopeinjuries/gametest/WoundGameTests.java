package com.github.littleemptydoll.lasthopeinjuries.gametest;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.wound.BodyPart;
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
                || soaked.dressing().cleanliness() >= 100 || soaked.bleedingPerSecond() <= freshBleeding
                || replaced.bleedingPerSecond() >= soaked.bleedingPerSecond()
                || replaced.removeDressing().bleedingPerSecond() != openBleeding
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
                covered.contamination(), covered.infection());
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
                0, null, 80, 82);
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
}
