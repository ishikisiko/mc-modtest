package com.example.myvillage.story;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;

public final class StoryAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,"myvillage");
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<StoryProgress>> PROGRESS = TYPES.register("story_progress",
            () -> AttachmentType.builder(() -> StoryProgress.EMPTY).serialize(StoryProgress.CODEC).copyOnDeath().build());
    private StoryAttachments() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
