package com.example.myvillage.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class QualityPickerItem extends Item {
    public QualityPickerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myvillage.quality_picker").withStyle(ChatFormatting.GRAY));
    }
}
