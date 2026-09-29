package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = LastHopeInjuries.MOD_ID, dist = Dist.CLIENT)
public final class LastHopeInjuriesClient {
    private static final KeyMapping OPEN_MEDICAL = new KeyMapping(
            "key.lasthopeinjuries.open_medical", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            ModList.get().isLoaded("legendarysurvivaloverhaul") ? GLFW.GLFW_KEY_J : GLFW.GLFW_KEY_H,
            "key.categories.lasthopeinjuries");

    public LastHopeInjuriesClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(MoodleHud::register);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MEDICAL);
    }

    static boolean isMedicalKey(int keyCode, int scanCode) {
        return OPEN_MEDICAL.matches(keyCode, scanCode);
    }

    @SubscribeEvent
    public void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            ClientWounds.reset();
            return;
        }
        while (OPEN_MEDICAL.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                PacketDistributor.sendToServer(new WoundNetwork.Request());
            }
        }
    }
}
