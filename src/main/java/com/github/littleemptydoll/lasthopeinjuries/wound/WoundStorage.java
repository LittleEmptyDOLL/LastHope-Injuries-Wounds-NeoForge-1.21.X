package com.github.littleemptydoll.lasthopeinjuries.wound;

import com.github.littleemptydoll.lasthopeinjuries.LastHopeInjuries;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WoundStorage {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LastHopeInjuries.MOD_ID);
    public static final Supplier<AttachmentType<WoundState>> WOUNDS = ATTACHMENTS.register(
            "wounds", () -> AttachmentType.builder(WoundState::empty).serialize(WoundState.CODEC).build());

    private WoundStorage() {}
}
