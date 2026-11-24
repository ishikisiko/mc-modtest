package com.example.myvillage.item;

import com.example.myvillage.alchemy.PillQuality;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A pill paid from the inventory when the corresponding trial starts. */
public final class TribulationPillItem extends Item {
    private final String id;

    public TribulationPillItem(Properties properties, String id) {
        super(properties.stacksTo(16));
        this.id = id;
    }

    public ItemStack findDose(Inventory inventory) {
        ItemStack dose = ItemStack.EMPTY;
        int quality = 5;
        for (ItemStack stack : inventory.items) {
            if (!stack.is(this)) continue;
            int candidateQuality = PillQuality.get(stack);
            if (candidateQuality < quality) {
                dose = stack;
                quality = candidateQuality;
                if (quality == 1) break;
            }
        }
        return dose;
    }

    @Override
    public Component getName(ItemStack stack) {
        int quality = PillQuality.get(stack);
        return PillQuality.name(quality).copy().append(super.getName(stack)).withColor(QualityDisplay.color(quality));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.yuanxian.alchemy.pill_" + id));
    }
}
