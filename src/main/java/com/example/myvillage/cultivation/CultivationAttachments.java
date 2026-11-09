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

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> FORGING_SKILL =
            ATTACHMENT_TYPES.register("forging_skill", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.intRange(0,10000000)).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CultivationProfile>> PROFILE =
            ATTACHMENT_TYPES.register("cultivation_profile", () -> AttachmentType
                    .builder(CultivationProfile::defaultProfile)
                    .serialize(CultivationProfile.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.cultivation.fate.FateState>> FATES =
            ATTACHMENT_TYPES.register("innate_fates", () -> AttachmentType
                    .builder(com.example.myvillage.cultivation.fate.FateState::empty)
                    .serialize(com.example.myvillage.cultivation.fate.FateState.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.cultivation.fate.SchoolInsightState>> SCHOOL_INSIGHT =
            ATTACHMENT_TYPES.register("school_insight", () -> AttachmentType
                    .builder(() -> com.example.myvillage.cultivation.fate.SchoolInsightState.EMPTY)
                    .serialize(com.example.myvillage.cultivation.fate.SchoolInsightState.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.cultivation.crafting.CraftingAptitudes>> CRAFTING =
            ATTACHMENT_TYPES.register("crafting_aptitudes", () -> AttachmentType
                    .builder(() -> com.example.myvillage.cultivation.crafting.CraftingAptitudes.EMPTY)
                    .serialize(com.example.myvillage.cultivation.crafting.CraftingAptitudes.CODEC)
                    .copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.alchemy.LearnedFormulas>> FORMULAS =
            ATTACHMENT_TYPES.register("learned_formulas", () -> AttachmentType
                    .builder(() -> com.example.myvillage.alchemy.LearnedFormulas.EMPTY)
                    .serialize(com.example.myvillage.alchemy.LearnedFormulas.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.alchemy.LearnedFormulas>> FORGE_PATTERNS =
            ATTACHMENT_TYPES.register("learned_forge_patterns", () -> AttachmentType
                    .builder(() -> com.example.myvillage.alchemy.LearnedFormulas.EMPTY)
                    .serialize(com.example.myvillage.alchemy.LearnedFormulas.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.cultivation.fate.EverlastingState>> EVERLASTING =
            ATTACHMENT_TYPES.register("everlasting_growth", () -> AttachmentType
                    .builder(() -> com.example.myvillage.cultivation.fate.EverlastingState.EMPTY)
                    .serialize(com.example.myvillage.cultivation.fate.EverlastingState.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.example.myvillage.aptitude.AptitudeState>> APTITUDES =
            ATTACHMENT_TYPES.register("aptitude_training", () -> AttachmentType
                    .builder(() -> com.example.myvillage.aptitude.AptitudeState.EMPTY)
                    .serialize(com.example.myvillage.aptitude.AptitudeState.CODEC).copyOnDeath().build());

    private CultivationAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
