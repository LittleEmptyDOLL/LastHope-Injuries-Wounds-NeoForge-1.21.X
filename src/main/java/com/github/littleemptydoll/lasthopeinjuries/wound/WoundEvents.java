package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoWoundBridge;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoRecoveryBridge;
import com.github.littleemptydoll.lasthopeinjuries.moodle.MoodleState;
import com.github.littleemptydoll.lasthopeinjuries.moodle.PsycheEvents;
import com.github.littleemptydoll.lasthopeinjuries.moodle.PsycheState;
import net.minecraft.stats.Stats;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.commands.Commands;

public final class WoundEvents {
    private static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "bleeding"));
    private static final ResourceKey<DamageType> WOUND_INFECTION = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(LastHopeInjuries.MOD_ID, "wound_infection"));
    private WoundEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Always release the snapshot, including for hits that cannot create wounds.
        BodyPart lsoPart = ModList.get().isLoaded("legendarysurvivaloverhaul")
                ? LsoWoundBridge.takeDamagedPart(player) : null;
        if (event.getNewDamage() < 1.0f) return;
        DamageSource source = event.getSource();
        // These damage types are physiological/environmental, and bleeding must not create another wound.
        if (source.is(BLEEDING) || source.is(WOUND_INFECTION)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        PsycheEvents.recordHit(player, event.getNewDamage());

        WoundGenerator.Cause cause = WoundGenerator.cause(source);
        if (cause == null) return;
        BodyPart part = lsoPart != null ? lsoPart : WoundGenerator.selectPart(cause, player.getRandom());
        EquipmentSlot slot = part.armorSlot();
        ItemStack armor = player.getItemBySlot(slot);
        double[] armorPoints = {0};
        armor.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                armorPoints[0] += modifier.amount();
            }
        });
        double condition = armor.isDamageableItem()
                ? Math.max(0.25, 1.0 - (double) armor.getDamageValue() / armor.getMaxDamage()) : 1.0;
        double points = source.is(DamageTypeTags.BYPASSES_ARMOR) ? 0 : armorPoints[0];
        Wound wound = WoundGenerator.roll(cause, event.getNewDamage(), part, points,
                condition, player.getRandom());
        if (wound != null) WoundService.add(player, wound);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        WoundState before = WoundService.get(player);
        if (before.wounds().isEmpty() && before.bloodLevel() >= 100) return;
        int exposure = player.isInWater() ? 4
                : player.level().isRainingAt(player.blockPosition()) ? 2 : 1;
        int hydration = ModList.get().isLoaded("legendarysurvivaloverhaul")
                ? LsoRecoveryBridge.hydration(player) : 20;
        int temperature = ModList.get().isLoaded("legendarysurvivaloverhaul")
                ? LsoRecoveryBridge.temperatureSeverity(player) : 0;
        int restTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        double recovery = RecoveryModifier.calculate(player.getFoodData().getFoodLevel(), hydration,
                temperature, restTicks, player.isSleeping(), MoodleState.from(before).sickness());
        WoundState after = before.advance(exposure, player.getRandom()::nextDouble, recovery);
        WoundService.set(player, after);
        // Severe depletion is harmful even after the bleeding has stopped.
        if (after.bloodLevel() <= 15 && player.tickCount % 100 == 0) {
            player.hurt(player.damageSources().source(BLEEDING), after.bloodLevel() <= 5 ? 2 : 1);
        }
        // Only severe bacterial wound infection causes periodic harm; zombie infection belongs to The Hordes.
        if (player.tickCount % 200 == 0 && after.wounds().stream().anyMatch(w -> w.infection() >= 80)) {
            player.hurt(player.damageSources().source(WOUND_INFECTION), 1);
        }
    }

    @SubscribeEvent
    public static void onOutgoingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        boolean painkiller = ModList.get().isLoaded("legendarysurvivaloverhaul")
                && LsoRecoveryBridge.painSuppressed(attacker);
        float factor = MoodleState.from(WoundService.get(attacker), painkiller).outgoingDamageMultiplier();
        factor *= PsycheState.damageMultiplier(PsycheEvents.get(attacker).panicLevel());
        if (factor < 1) event.setNewDamage(event.getNewDamage() * factor);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wounds")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    WoundState state = WoundService.get(player);
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Blood level: " + Math.round(state.bloodLevel()) + "/100; bleeding: "
                                    + String.format(java.util.Locale.ROOT, "%.2f", state.bleedingRate())
                                    + "/s; wounds: " + state.wounds().size()), false);
                    for (Wound wound : state.wounds()) {
                        context.getSource().sendSuccess(() -> Component.literal(
                                        wound.id() + " " + wound.part() + " " + wound.type()
                                        + " severity=" + wound.effectiveSeverity()
                                        + " healing=" + Math.round(wound.healingProgress()) + "%"
                                        + " infection=" + wound.infection()
                                        + " dressing=" + (wound.dressing() == null ? "none"
                                        : wound.dressing().item() + " clean=" + Math.round(wound.dressing().cleanliness())
                                        + " soaked=" + Math.round(wound.dressing().saturation()))), false);
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
