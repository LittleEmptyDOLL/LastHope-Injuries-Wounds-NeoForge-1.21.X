package com.github.littleemptydoll.lasthopeinjuries;

import com.github.littleemptydoll.lasthopeinjuries.wound.WoundEvents;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundStorage;
import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoWoundBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

@Mod(LastHopeInjuries.MOD_ID)
public final class LastHopeInjuries {
    public static final String MOD_ID = "lasthopeinjuries";

    public LastHopeInjuries(IEventBus modBus) {
        WoundStorage.ATTACHMENTS.register(modBus);
        modBus.addListener(WoundNetwork::register);
        NeoForge.EVENT_BUS.register(WoundEvents.class);
        if (ModList.get().isLoaded("legendarysurvivaloverhaul")) {
            NeoForge.EVENT_BUS.register(LsoWoundBridge.class);
        }
    }
}
