package com.example.myvillage.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components carried by technique manuals (秘籍): which technique, and how far it has been studied. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "myvillage");

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

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
