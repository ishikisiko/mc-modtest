package com.example.myvillage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** Ordinary forging additives; consumption remains in the server forge. */
public final class ForgingMaterialItem extends Item {
    private final String materialId;
    public ForgingMaterialItem(Properties properties,String materialId) {
        super(properties);this.materialId=materialId;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.myvillage.forging_material."+materialId).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.myvillage.forging_material.use").withStyle(ChatFormatting.GRAY));
    }
}
