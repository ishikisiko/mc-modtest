package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** Presentation only: zero is an unmarked weapon, never a saved ForgeMark quality. */
public final class ForgingQualityDisplay {
    private ForgingQualityDisplay() {}

    public static int quality(ItemStack stack) {
        var mark = stack.get(ModDataComponents.FORGE_MARK.get());
        return mark == null ? 0 : mark.quality();
    }

    public static int color(int quality) {
        return switch (quality) {
            case 0 -> 0xFFFFFF;
            case 1 -> 0x55FF55;
            case 2 -> 0x5555FF;
            case 3 -> 0xAA55FF;
            case 4 -> 0xFFAA00;
            default -> throw new IllegalArgumentException("Invalid displayed forging quality: " + quality);
        };
    }

    public static MutableComponent name(int quality) {
        return Component.translatable("forging.myvillage.quality." + quality)
                .withStyle(style -> style.withColor(color(quality)));
    }
}
