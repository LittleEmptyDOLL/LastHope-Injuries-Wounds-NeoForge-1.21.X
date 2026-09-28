package com.github.littleemptydoll.lasthopeinjuries.wound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

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

    public static boolean bandage(ServerPlayer player, UUID id) {
        return change(player, id, Wound::bandage, wound -> !wound.bandaged());
    }

    public static boolean clean(ServerPlayer player, UUID id) {
        return change(player, id, Wound::clean,
                wound -> wound.contamination() > 0 || wound.infection() > 0);
    }

    /** Called by network handlers; the item, hand and target are checked on the server. */
    public static boolean treat(ServerPlayer player, UUID id, boolean bandage, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = player.getItemInHand(hand);
        TagKey<Item> tag = bandage ? TreatmentItems.BANDAGES : TreatmentItems.ANTISEPTICS;
        if (!stack.is(tag)) return false;
        boolean changed = bandage ? bandage(player, id) : clean(player, id);
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
