package com.example.myvillage.orchard;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

final class OrchardItems {
    static final class PlantBlockItem extends BlockItem {
        PlantBlockItem(Block block,Properties properties) { super(block,properties); }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
            super.appendHoverText(stack,context,lines,flag);
            String hint=getBlock() instanceof NingyuanSaplingBlock?"sapling":getBlock() instanceof NingyuanFruitBlock?"fruit_cluster":null;
            if(hint!=null) lines.add(Component.translatable("tooltip.myvillage.ningyuan_"+hint).withStyle(ChatFormatting.GRAY));
        }
    }
    static final class FertilizerItem extends Item {
        FertilizerItem(Properties properties) { super(properties); }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
            lines.add(Component.translatable("tooltip.myvillage.ningyuan_fertilizer").withStyle(ChatFormatting.GRAY));
        }
    }
    private OrchardItems() { }
}
