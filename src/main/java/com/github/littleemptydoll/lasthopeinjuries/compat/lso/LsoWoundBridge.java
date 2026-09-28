package com.github.littleemptydoll.lasthopeinjuries.compat.lso;

import com.github.littleemptydoll.lasthopeinjuries.wound.BodyPart;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyDamageUtil;
import sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyPartEnum;
import sfiomn.legendarysurvivaloverhaul.util.AttachmentUtil;

/** Loaded only when LSO is present. Its LOWEST Pre handler assigns the actual hit limb. */
public final class LsoWoundBridge {
    private static final Map<UUID, float[]> BEFORE_DAMAGE = new HashMap<>();

    private LsoWoundBridge() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureBeforeDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var attachment = AttachmentUtil.getBodyDamageAttachment(player);
        BodyPartEnum[] parts = BodyPartEnum.values();
        float[] snapshot = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            snapshot[i] = attachment.getBodyPartDamage(parts[i]);
        }
        BEFORE_DAMAGE.put(player.getUUID(), snapshot);
    }

    /** Called by our Post handler after LSO has assigned the limb damage. */
    public static BodyPart takeDamagedPart(ServerPlayer player) {
        float[] snapshot = BEFORE_DAMAGE.remove(player.getUUID());
        if (snapshot == null) return null;
        var attachment = AttachmentUtil.getBodyDamageAttachment(player);
        BodyPartEnum[] parts = BodyPartEnum.values();
        float largestIncrease = 0;
        BodyPart selected = null;
        for (int i = 0; i < parts.length; i++) {
            float increase = attachment.getBodyPartDamage(parts[i]) - snapshot[i];
            if (increase > largestIncrease) {
                largestIncrease = increase;
                selected = BodyPart.valueOf(parts[i].name());
            }
        }
        // LSO can disable localized damage, block a hit, or have a full limb.
        return selected;
    }

    public static float healthRatio(Player player, BodyPart part) {
        return Math.max(0, Math.min(1, BodyDamageUtil.getHealthRatio(player, BodyPartEnum.valueOf(part.name()))));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BEFORE_DAMAGE.remove(event.getEntity().getUUID());
    }
}
