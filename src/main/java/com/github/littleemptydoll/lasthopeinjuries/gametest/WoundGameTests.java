package com.github.littleemptydoll.lasthopeinjuries.gametest;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.wound.BodyPart;
import com.github.littleemptydoll.lasthopeinjuries.wound.Wound;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundType;
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
        WoundState original = new WoundState(List.of(wound), 0);
        var encoded = WoundState.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        WoundState decoded = WoundState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        WoundState advanced = decoded.advance();
        if (!decoded.equals(original) || advanced.wounds().size() != 1 || advanced.bloodLoss() <= 0) {
            helper.fail("Wound state failed persistence or bleeding progression");
        } else {
            helper.succeed();
        }
    }
}
