package com.example.myvillage.cultivation;

import com.example.myvillage.MyVillageMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CultivationAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MyVillageMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CultivationProfile>> PROFILE =
            ATTACHMENT_TYPES.register("cultivation_profile", () -> AttachmentType
                    .builder(CultivationProfile::defaultProfile)
                    .serialize(CultivationProfile.CODEC)
                    .copyOnDeath()
                    .build());

    private CultivationAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
