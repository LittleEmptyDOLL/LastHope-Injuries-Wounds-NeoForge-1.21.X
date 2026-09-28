package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.commands.Commands;

public final class WoundEvents {
    private static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "bleeding"));
    private WoundEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() < 1.0f) return;
        DamageSource source = event.getSource();
        // These damage types are physiological/environmental, and bleeding must not create another wound.
        if (source.is(BLEEDING) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.MAGIC)) return;

        WoundType type = selectType(player, source, event.getNewDamage());
        BodyPart part = selectPart(player, source);
        EquipmentSlot slot = part.armorSlot();
        boolean covered = player.getItemBySlot(slot).getItem() instanceof ArmorItem;
        double resistance = covered ? protection(type) : 0.0;
        double chance = Math.min(0.85, 0.16 + event.getNewDamage() * 0.055) * (1 - resistance);
        if (player.getRandom().nextDouble() >= chance) return;

        int severity = Math.max(1, Math.min(5, (int) Math.ceil(event.getNewDamage() / 3.0)));
        if (covered && resistance >= 0.5 && severity > 1) severity--;
        WoundService.add(player, Wound.create(part, type, severity));
    }

    private static WoundType selectType(ServerPlayer player, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FIRE)) return WoundType.BURN;
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypeTags.IS_EXPLOSION)) return WoundType.BRUISE;
        if (source.getDirectEntity() instanceof Projectile) return WoundType.PUNCTURE;
        if (source.getEntity() instanceof Zombie && player.getRandom().nextFloat() < 0.25f) return WoundType.BITE;
        if (damage >= 8) return WoundType.DEEP_LACERATION;
        return damage >= 3 ? WoundType.LACERATION : WoundType.SCRATCH;
    }

    private static BodyPart selectPart(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypes.FALL)) {
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        }
        BodyPart[] parts = BodyPart.values();
        return parts[player.getRandom().nextInt(parts.length)];
    }

    private static double protection(WoundType type) {
        return switch (type) {
            case BRUISE -> 0.25;
            case PUNCTURE, BITE -> 0.35;
            case BURN -> 0.40;
            case SCRATCH, LACERATION, DEEP_LACERATION -> 0.60;
        };
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        WoundState before = WoundService.get(player);
        if (before.wounds().isEmpty() && before.bloodLoss() <= 0) return;
        WoundState after = before.advance();
        player.setData(WoundStorage.WOUNDS, after);
        // Infrequent secondary damage with its own death message and recursion guard.
        if (after.bloodLoss() >= 25 && player.tickCount % 100 == 0) {
            player.hurt(player.damageSources().source(BLEEDING), after.bloodLoss() >= 75 ? 2 : 1);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wounds")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    WoundState state = WoundService.get(player);
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Blood loss: " + Math.round(state.bloodLoss()) + "/100; wounds: " + state.wounds().size()), false);
                    for (Wound wound : state.wounds()) {
                        context.getSource().sendSuccess(() -> Component.literal(
                                wound.id() + " " + wound.part() + " " + wound.type()
                                        + " severity=" + wound.severity()
                                        + " infection=" + wound.infection()
                                        + " bandaged=" + wound.bandaged()), false);
                    }
                    return state.wounds().size();
                }))
                .then(Commands.literal("add")
                        .then(Commands.argument("part", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("severity", IntegerArgumentType.integer(1, 5))
                                                .executes(context -> {
                                                    try {
                                                        BodyPart part = BodyPart.valueOf(StringArgumentType.getString(context, "part").toUpperCase(java.util.Locale.ROOT));
                                                        WoundType type = WoundType.valueOf(StringArgumentType.getString(context, "type").toUpperCase(java.util.Locale.ROOT));
                                                        int severity = IntegerArgumentType.getInteger(context, "severity");
                                                        return WoundService.add(context.getSource().getPlayerOrException(),
                                                                Wound.create(part, type, severity)) ? 1 : 0;
                                                    } catch (IllegalArgumentException invalid) {
                                                        context.getSource().sendFailure(Component.literal("Unknown body part or wound type"));
                                                        return 0;
                                                    }
                                                })))))
                .then(Commands.literal("bandage").then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> modify(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "id"), true))))
                .then(Commands.literal("clean").then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> modify(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "id"), false)))));
    }

    private static int modify(ServerPlayer player, String rawId, boolean bandage) {
        try {
            UUID id = UUID.fromString(rawId);
            return (bandage ? WoundService.bandage(player, id) : WoundService.clean(player, id)) ? 1 : 0;
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
    }
}
