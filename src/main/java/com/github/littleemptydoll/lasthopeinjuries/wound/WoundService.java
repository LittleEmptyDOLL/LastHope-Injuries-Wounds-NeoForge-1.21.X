package com.github.littleemptydoll.lasthopeinjuries.wound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server authority for wound state. Client screens and LSO item hooks can call these operations later. */
public final class WoundService {
    private static final int MAX_WOUNDS = 24;

    private WoundService() {}

    public static WoundState get(ServerPlayer player) {
        return player.getData(WoundStorage.WOUNDS);
    }

    public static boolean add(ServerPlayer player, Wound wound) {
        WoundState state = get(player);
        if (state.wounds().size() >= MAX_WOUNDS) return false;
        List<Wound> wounds = new ArrayList<>(state.wounds());
        wounds.add(wound);
        set(player, new WoundState(wounds, state.bloodLevel()));
        return true;
    }

    public static boolean aggravate(ServerPlayer player, BodyPart part, float healthDamage) {
        WoundState before = get(player);
        WoundState after = before.aggravate(part, healthDamage, player.getRandom().nextDouble());
        if (after == before) return false;
        set(player, after);
        return true;
    }

    public static boolean bandage(ServerPlayer player, UUID id) {
        return change(player, id, Wound::bandage, Wound::canBandage);
    }

    public static boolean removeDressing(ServerPlayer player, UUID id) {
        if (!player.isAlive() || player.isSpectator()) return false;
        return change(player, id, Wound::removeDressing, Wound::bandaged);
    }

    public static boolean clean(ServerPlayer player, UUID id) {
        return change(player, id, Wound::clean,
                wound -> !wound.bandaged() && wound.contamination() > 0);
    }

    public static boolean antibiotics(ServerPlayer player, UUID id) {
        return change(player, id, Wound::antibiotics, wound -> wound.infection() > 0);
    }

    /** Called by network handlers; the inventory item and target are checked on the server. */
    public static boolean treat(ServerPlayer player, UUID id, int action) {
        if (!player.isAlive() || player.isSpectator()) return false;
        TagKey<Item> tag = switch (action) {
            case 0 -> TreatmentItems.BANDAGES;
            case 1 -> TreatmentItems.ANTISEPTICS;
            case 3 -> TreatmentItems.ANTIBIOTICS;
            case 4 -> TreatmentItems.MEDKITS;
            case 5 -> TreatmentItems.HERBS;
            case 6 -> TreatmentItems.SUTURES;
            default -> null;
        };
        if (tag == null) return false;
        // Prefer the selected slot, then the offhand, then the rest of the carried inventory.
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(tag)) stack = player.getOffhandItem();
        if (!stack.is(tag)) {
            stack = ItemStack.EMPTY;
            for (ItemStack candidate : player.getInventory().items) {
                if (candidate.is(tag)) {
                    stack = candidate;
                    break;
                }
            }
        }
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean changed = switch (action) {
            case 0 -> change(player, id, wound -> wound.bandage(itemId), Wound::canBandage);
            case 1 -> clean(player, id);
            case 3 -> antibiotics(player, id);
            case 4 -> change(player, id, wound -> wound.medkit(itemId), Wound::canMedkit);
            case 5 -> player.getFoodData().getFoodLevel() > 3
                    && change(player, id, Wound::herbs, Wound::canUseHerbs);
            case 6 -> change(player, id, Wound::suture, Wound::canSuture);
            default -> false;
        };
        if (changed && !player.getAbilities().instabuild) stack.shrink(1);
        return changed;
    }

    public static void set(ServerPlayer player, WoundState state) {
        player.setData(WoundStorage.WOUNDS, state);
        WoundNetwork.sync(player, state, false);
    }

    private static boolean change(ServerPlayer player, UUID id, UnaryOperator<Wound> operation,
                                  java.util.function.Predicate<Wound> canChange) {
        WoundState state = get(player);
        List<Wound> wounds = new ArrayList<>(state.wounds());
        for (int index = 0; index < wounds.size(); index++) {
            if (wounds.get(index).id().equals(id) && canChange.test(wounds.get(index))) {
                wounds.set(index, operation.apply(wounds.get(index)));
                set(player, new WoundState(wounds, state.bloodLevel()));
                return true;
            }
        }
        return false;
    }
}
