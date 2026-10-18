package com.example.myvillage.item;

import com.mojang.serialization.Codec;
import com.example.myvillage.alchemy.PillQuality;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent synchronized metadata for manuals and quality-bearing medicines. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "myvillage");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.example.myvillage.forging.CopperInfusion>> COPPER_INFUSION =
            DATA_COMPONENTS.registerComponentType("copper_infusion", builder -> builder
                    .persistent(com.example.myvillage.forging.CopperInfusion.CODEC)
                    .networkSynchronized(com.example.myvillage.forging.CopperInfusion.STREAM_CODEC));

    /** The technique a manual teaches. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TECHNIQUE =
            DATA_COMPONENTS.registerComponentType("technique", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** Study points already comprehended from a manual; absent means 0. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COMPREHENSION =
            DATA_COMPONENTS.registerComponentType("comprehension", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Same-recipe pill quality; absence means the legacy lower-quality baseline. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PILL_QUALITY =
            DATA_COMPONENTS.registerComponentType("pill_quality", builder -> builder
                    .persistent(PillQuality.CODEC)
                    .networkSynchronized(PillQuality.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.example.myvillage.forging.ForgeMark>> FORGE_MARK =
            DATA_COMPONENTS.registerComponentType("forge_mark", builder -> builder
                    .persistent(com.example.myvillage.forging.ForgeMark.CODEC)
                    .networkSynchronized(com.example.myvillage.forging.ForgeMark.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.example.myvillage.spiritstone.SpiritStoneLedger>> SPIRIT_STONE_BALANCE =
            DATA_COMPONENTS.register("spirit_stone_balance", () -> com.example.myvillage.spiritstone.SpiritStoneLedger.TYPE);

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
