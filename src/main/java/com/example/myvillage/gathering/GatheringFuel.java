package com.example.myvillage.gathering;

import com.example.myvillage.spiritstone.SpiritStoneDenominations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Membership comes from the shared denomination catalogue; overlapping grades are rejected. */
public enum GatheringFuel {
    LOW("low_grade", "下品"), MEDIUM("medium_grade", "中品"), HIGH("high_grade", "上品");
    public final String id, label;
    GatheringFuel(String id, String label) { this.id = id; this.label = label; }
    public int wire() { return ordinal() + 1; }
    public static GatheringFuel fromWire(int code) { return code > 0 && code <= values().length ? values()[code - 1] : null; }
    public static GatheringFuel fromId(String id) {
        for (var grade : values()) if (grade.id.equals(id)) return grade;
        return null;
    }
    public static GatheringFuel of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        GatheringFuel result = null;
        for (var fuel : values()) {
            var tier = SpiritStoneDenominations.get().tier(ResourceLocation.fromNamespaceAndPath("myvillage", fuel.id));
            if (stack.is(tier.tag())) {
                if (result != null) return null;
                result = fuel;
            }
        }
        return result;
    }
}
