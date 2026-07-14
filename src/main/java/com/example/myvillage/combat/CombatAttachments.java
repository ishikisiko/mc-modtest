package com.example.myvillage.combat;

import com.example.myvillage.MyVillageMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CombatAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MyVillageMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatPreference>> PREFERENCE =
            ATTACHMENT_TYPES.register("combat_preference", () -> AttachmentType
                    .builder(CombatPreference::defaultPreference)
                    .serialize(CombatPreference.CODEC)
                    .copyOnDeath()
                    .build());

    private CombatAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
