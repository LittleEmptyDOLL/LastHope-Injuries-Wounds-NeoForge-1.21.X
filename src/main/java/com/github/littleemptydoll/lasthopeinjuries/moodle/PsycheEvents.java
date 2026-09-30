package com.github.littleemptydoll.lasthopeinjuries.moodle;

import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for transient stress; scan for nearby danger only once per second. */
public final class PsycheEvents {
    private static final Map<UUID, PsycheState> STATES = new HashMap<>();

    private PsycheEvents() {}

    public static PsycheState get(ServerPlayer player) {
        return STATES.getOrDefault(player.getUUID(), PsycheState.CALM);
    }

    public static void recordHit(ServerPlayer player, float damage) {
        STATES.put(player.getUUID(), get(player).hit(damage));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (player.isSpectator()) {
            STATES.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new WoundNetwork.PsycheSync(0, 0));
            return;
        }
        int hostiles = player.level().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(10), monster -> monster.isAlive()).size();
        PsycheState state = get(player).advance(MoodleState.from(WoundService.get(player)),
                hostiles, player.isSleeping());
        STATES.put(player.getUUID(), state);
        // Small independent packet: movement and hostile proximity change without wound updates.
        PacketDistributor.sendToPlayer(player, new WoundNetwork.PsycheSync(
                state.stressLevel(), state.panicLevel()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
