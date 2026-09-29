package com.github.littleemptydoll.lasthopeinjuries.compat.lso;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.util.AttachmentUtil;
import sfiomn.legendarysurvivaloverhaul.registry.MobEffectRegistry;

/** Call only when LSO is loaded; this reads its state without changing its survival systems. */
public final class LsoRecoveryBridge {
    private LsoRecoveryBridge() {}

    public static int hydration(ServerPlayer player) {
        return ThirstUtil.isThirstActive(player)
                ? AttachmentUtil.getThirstAttachment(player).getHydrationLevel() : 20;
    }

    public static int temperatureSeverity(ServerPlayer player) {
        TemperatureEnum level = AttachmentUtil.getTempAttachment(player).getTemperatureEnum();
        return switch (level) {
            case COLD, HOT -> 1;
            case FROSTBITE, HEAT_STROKE -> 2;
            case NORMAL -> 0;
        };
    }

    public static boolean painSuppressed(Player player) {
        // Morphine's own LSO effect retains its normal duration and addiction handling.
        return player.hasEffect(MobEffectRegistry.PAINKILLER);
    }
}
