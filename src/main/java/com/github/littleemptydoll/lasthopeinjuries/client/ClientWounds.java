package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import net.minecraft.client.Minecraft;

public final class ClientWounds {
    private static WoundState state = WoundState.empty();

    private ClientWounds() {}

    public static WoundState state() { return state; }

    public static void reset() { state = WoundState.empty(); }

    public static void receive(WoundState next, boolean open) {
        state = next;
        if (open && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen(new MedicalScreen());
        }
    }
}
