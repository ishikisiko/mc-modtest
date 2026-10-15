package com.example.myvillage.alchemy;

import com.example.myvillage.item.ModDataComponents;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** One pill item per recipe; strength travels in a bounded persistent component. */
public final class PillQuality {
    public static final Codec<Integer> CODEC = Codec.intRange(1, 4);
    public static final StreamCodec<ByteBuf, Integer> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PillQuality::require, PillQuality::require);

    private PillQuality() { }

    public static int get(ItemStack stack) { return get(stack, ModDataComponents.PILL_QUALITY.get()); }

    static int get(ItemStack stack, DataComponentType<Integer> type) {
        Integer value = stack.get(type);
        return value == null || value < 1 || value > 4 ? 1 : value;
    }

    /** Lower quality omits the component so newly brewed pills stack with legacy ones. */
    public static void apply(ItemStack stack, int quality) {
        apply(stack, quality, ModDataComponents.PILL_QUALITY.get());
    }

    static void apply(ItemStack stack, int quality, DataComponentType<Integer> type) {
        require(quality);
        if (quality == 1) stack.remove(type);
        else stack.set(type, quality);
    }

    public static Component name(int quality) {
        return Component.translatable("alchemy.myvillage.quality." + require(quality));
    }

    public static int require(int quality) {
        if (quality < 1 || quality > 4) throw new IllegalArgumentException("Pill quality must be one through four");
        return quality;
    }
}
