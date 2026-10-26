package com.example.myvillage.talisman;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import com.example.myvillage.item.QualityDisplay;

public final class TalismanQuality {
    private TalismanQuality() { }
    public static int require(int quality) {
        if (quality < 1 || quality > 4) throw new IllegalArgumentException("符箓品质越界");
        return quality;
    }
    public static int get(ItemStack stack) { return require(stack.getOrDefault(TalismanContent.QUALITY.get(), 1)); }
    public static void apply(ItemStack stack, int quality) {
        require(quality);
        if (quality == 1) stack.remove(TalismanContent.QUALITY.get()); else stack.set(TalismanContent.QUALITY.get(), quality);
    }
    public static Component name(int quality) {
        return Component.literal(new String[]{"下品", "中品", "上品", "极品"}[require(quality) - 1])
                .withStyle(s -> s.withColor(QualityDisplay.color(quality)));
    }
}
