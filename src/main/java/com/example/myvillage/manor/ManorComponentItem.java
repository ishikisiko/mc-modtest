package com.example.myvillage.manor;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class ManorComponentItem extends BlockItem {
    public ManorComponentItem(ManorComponentBlock block,Properties properties){super(block,properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.myvillage.manor_component").withStyle(ChatFormatting.GRAY));
    }
}
