package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Small moodles independent of the vanilla potion-effect HUD. */
public final class BloodHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(
            LastHopeInjuries.MOD_ID, "blood_status");

    private BloodHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, BloodHud::render);
    }

    private static void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        WoundState state = ClientWounds.state();
        float bleeding = state.bleedingRate();
        if (bleeding <= 0 && state.bloodLevel() >= 99.95F) return;

        int x = gui.guiWidth() - 116;
        int y = 7;
        if (bleeding > 0) {
            String stage = bleeding < 0.10F ? "I" : bleeding < 0.30F ? "II" : "III";
            drawLine(gui, minecraft, x, y,
                    Component.translatable("hud.lasthopeinjuries.bleeding", stage), 0xFFFF7777);
            y += 17;
        }
        if (state.bloodLevel() < 99.95F) {
            int color = state.bloodLevel() < 30 ? 0xFFFF6666
                    : state.bloodLevel() < 60 ? 0xFFFFAA77 : 0xFFFFDDDD;
            drawLine(gui, minecraft, x, y,
                    Component.translatable("hud.lasthopeinjuries.blood_level",
                            Math.round(state.bloodLevel())), color);
        }
    }

    private static void drawLine(GuiGraphics gui, Minecraft minecraft, int x, int y,
                                 Component label, int color) {
        gui.fill(x, y, x + 109, y + 15, 0xBB151B22);
        gui.fill(x, y, x + 2, y + 15, color);
        gui.drawString(minecraft.font, label, x + 6, y + 3, color);
    }
}
