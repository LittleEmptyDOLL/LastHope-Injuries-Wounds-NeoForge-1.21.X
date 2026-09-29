package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoRecoveryBridge;
import com.github.littleemptydoll.lasthopeinjuries.moodle.MoodleState;
import com.github.littleemptydoll.lasthopeinjuries.moodle.MoodleType;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.fml.ModList;

/** The medical moodles occupy their own HUD layer, separate from potion effects. */
public final class MoodleHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(
            LastHopeInjuries.MOD_ID, "medical_moodles");
    private static final String[] NUMERALS = {"", "I", "II", "III", "IV"};

    private MoodleHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, MoodleHud::render);
    }

    private static void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        WoundState wounds = ClientWounds.state();
        boolean painkiller = ModList.get().isLoaded("legendarysurvivaloverhaul")
                && LsoRecoveryBridge.painSuppressed(minecraft.player);
        MoodleState moodles = MoodleState.from(wounds, painkiller);
        int x = gui.guiWidth() - 170;
        int y = 7;
        for (MoodleType type : MoodleType.values()) {
            int level = moodles.level(type);
            if (level == 0) continue;
            String key = "hud.lasthopeinjuries." + type.name().toLowerCase(java.util.Locale.ROOT);
            Component label = type == MoodleType.BLOOD_LOSS
                    ? Component.translatable(key, NUMERALS[level], Math.round(wounds.bloodLevel()))
                    : Component.translatable(key, NUMERALS[level]);
            int color = color(type, level);
            gui.fill(x, y, x + 163, y + 15, 0xBB151B22);
            gui.fill(x, y, x + 2, y + 15, color);
            gui.drawString(minecraft.font, label, x + 6, y + 3, color);
            y += 17;
        }
    }

    private static int color(MoodleType type, int level) {
        if (level >= 4) return 0xFFFF5555;
        return switch (type) {
            case BLEEDING -> 0xFFFF8888;
            case BLOOD_LOSS -> level >= 3 ? 0xFFFF8888 : 0xFFFFC0B5;
            case PAIN -> 0xFFFFB870;
            case SICKNESS -> 0xFFBBDD8A;
        };
    }
}
