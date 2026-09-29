package com.github.littleemptydoll.lasthopeinjuries.network;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.client.ClientWounds;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundService;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class WoundNetwork {
    private WoundNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC,
                (payload, context) -> ClientWounds.receive(payload.state(), payload.open()));
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                sync(player, WoundService.get(player), true);
            }
        });
        registrar.playToServer(Treat.TYPE, Treat.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                UUID id = UUID.fromString(payload.id());
                if (payload.action() < 0 || payload.action() > 3
                        || payload.hand() < 0 || payload.hand() > 1) return;
                if (payload.action() == 2) {
                    WoundService.removeDressing(player, id);
                } else {
                    WoundService.treat(player, id, payload.action(),
                            payload.hand() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid or obsolete client selection. Never trust packet-supplied wound IDs.
            }
        });
    }

    public static void sync(ServerPlayer player, WoundState state, boolean open) {
        PacketDistributor.sendToPlayer(player, new Sync(state, open));
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "request_wounds"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Sync(WoundState state, boolean open) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "sync_wounds"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.fromCodecWithRegistries(WoundState.CODEC), Sync::state,
                ByteBufCodecs.BOOL, Sync::open, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Treat(String id, int action, int hand) implements CustomPacketPayload {
        public static final Type<Treat> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "treat_wound"));
        public static final StreamCodec<ByteBuf, Treat> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Treat::id,
                ByteBufCodecs.VAR_INT, Treat::action,
                ByteBufCodecs.VAR_INT, Treat::hand, Treat::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
