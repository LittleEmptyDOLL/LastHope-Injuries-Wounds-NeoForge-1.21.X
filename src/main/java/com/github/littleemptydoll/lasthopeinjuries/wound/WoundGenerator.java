package com.github.littleemptydoll.lasthopeinjuries.wound;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;

/** Translates a health-damaging hit into a localized physical wound. */
public final class WoundGenerator {
    public enum Cause { FALL, IMPACT, BLAST, PROJECTILE, PIERCE, ABRASION, FIRE, BITE, SHARP, MELEE }

    private WoundGenerator() {}

    public static Cause cause(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FREEZE)) return null;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LIGHTNING_BOLT)) return Cause.FIRE;
        if (source.is(DamageTypes.FALL)) return Cause.FALL;
        if (source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK)) return Cause.IMPACT;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Cause.BLAST;
        if (source.getDirectEntity() instanceof Projectile) return Cause.PROJECTILE;
        if (source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)) return Cause.PIERCE;
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) return Cause.ABRASION;
        if (source.getEntity() instanceof Zombie && source.getDirectEntity() == source.getEntity()) return Cause.BITE;
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            return attacker.getMainHandItem().is(ItemTags.SWORDS) || attacker.getMainHandItem().is(ItemTags.AXES)
                    ? Cause.SHARP : Cause.MELEE;
        }
        return null; // Non-physical damage (including other mods' effects) cannot create a wound.
    }

    /** Armor points are read from the actual equipped stack, not the player's combined armor value. */
    public static Wound roll(Cause cause, float healthDamage, BodyPart part,
                             double armorPoints, double armorCondition, RandomSource random) {
        if (cause == null || healthDamage < 1) return null;
        WoundType type = selectType(cause, healthDamage, random);
        double resistance = protection(type, armorPoints, armorCondition);
        double chance = Math.min(0.85, 0.16 + healthDamage * 0.055) * (1 - resistance);
        if (random.nextDouble() >= chance) return null;
        int severity = Math.max(1, Math.min(5, (int) Math.ceil(healthDamage / 3.0)));
        if (resistance >= 0.5 && severity > 1) severity--;
        return Wound.create(part, type, severity);
    }

    public static BodyPart selectPart(Cause cause, RandomSource random) {
        // Index order matches BodyPart.values(): head, chest, arms, legs, feet.
        int[] weights = switch (cause) {
            case FALL -> new int[] {0, 0, 0, 0, 2, 2, 3, 3};
            case BITE, MELEE, SHARP -> new int[] {1, 3, 3, 3, 2, 2, 1, 1};
            case PROJECTILE, PIERCE -> new int[] {1, 4, 2, 2, 2, 2, 1, 1};
            case IMPACT -> new int[] {2, 3, 1, 1, 2, 2, 1, 1};
            case BLAST, ABRASION, FIRE -> new int[] {1, 3, 2, 2, 2, 2, 1, 1};
        };
        int total = 0;
        for (int weight : weights) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return BodyPart.values()[i];
        }
        throw new IllegalStateException("Invalid body part weights");
    }

    private static WoundType selectType(Cause cause, float damage, RandomSource random) {
        double roll = random.nextDouble();
        return switch (cause) {
            case FIRE -> WoundType.BURN;
            case FALL, IMPACT -> WoundType.BRUISE;
            case PROJECTILE, PIERCE -> WoundType.PUNCTURE;
            case ABRASION -> damage >= 5 && roll < 0.35 ? WoundType.LACERATION : WoundType.SCRATCH;
            case BLAST -> roll < 0.60 ? WoundType.BRUISE
                    : damage >= 8 && roll > 0.88 ? WoundType.DEEP_LACERATION : WoundType.LACERATION;
            case BITE -> roll < 0.35 ? WoundType.BITE : cut(damage, roll);
            case SHARP -> roll < 0.08 ? WoundType.SCRATCH : cut(damage + 2, roll);
            case MELEE -> roll < 0.55 ? WoundType.BRUISE : cut(damage, roll);
        };
    }

    private static WoundType cut(float damage, double roll) {
        if (damage >= 8 && roll > 0.65) return WoundType.DEEP_LACERATION;
        return damage >= 3 ? WoundType.LACERATION : WoundType.SCRATCH;
    }

    public static double protection(WoundType type, double armorPoints, double condition) {
        if (armorPoints <= 0) return 0;
        double coverage = switch (type) {
            case BRUISE -> 0.30;
            case PUNCTURE, BITE -> 0.40;
            case BURN -> 0.45;
            case SCRATCH, LACERATION, DEEP_LACERATION -> 0.70;
        };
        return Math.min(0.75, coverage * (0.5 + Math.min(10, armorPoints) / 10.0))
                * Math.max(0, Math.min(1, condition));
    }
}
