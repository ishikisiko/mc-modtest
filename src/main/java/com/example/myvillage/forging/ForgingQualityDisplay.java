package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.QualityDisplay;
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
        return QualityDisplay.color(quality);
    }

    public static MutableComponent name(int quality) {
        return Component.translatable("forging.myvillage.quality." + quality)
                .withStyle(style -> style.withColor(color(quality)));
    }
}
