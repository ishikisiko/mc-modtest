package com.example.myvillage.item;

import com.example.myvillage.alchemy.PillQuality;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared presentation only; each item family retains its own saved quality rules. */
public final class QualityDisplay {
    private QualityDisplay() {}

    public static boolean supports(Item item) {
        return item instanceof CombatWeaponItem || item instanceof AlchemyPillItem;
    }

    public static int quality(ItemStack stack) {
        if (stack.getItem() instanceof AlchemyPillItem) return PillQuality.get(stack);
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
            default -> throw new IllegalArgumentException("Invalid displayed quality: " + quality);
        };
    }
}
