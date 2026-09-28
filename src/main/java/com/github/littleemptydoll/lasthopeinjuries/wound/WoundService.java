package com.github.littleemptydoll.lasthopeinjuries.wound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerPlayer;

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
        player.setData(WoundStorage.WOUNDS, new WoundState(wounds, state.bloodLoss()));
        return true;
    }

    public static boolean bandage(ServerPlayer player, UUID id) {
        return change(player, id, Wound::bandage);
    }

    public static boolean clean(ServerPlayer player, UUID id) {
        return change(player, id, Wound::clean);
    }

    private static boolean change(ServerPlayer player, UUID id, UnaryOperator<Wound> operation) {
        WoundState state = get(player);
        List<Wound> wounds = new ArrayList<>(state.wounds());
        for (int index = 0; index < wounds.size(); index++) {
            if (wounds.get(index).id().equals(id)) {
                wounds.set(index, operation.apply(wounds.get(index)));
                player.setData(WoundStorage.WOUNDS, new WoundState(wounds, state.bloodLoss()));
                return true;
            }
        }
        return false;
    }
}
