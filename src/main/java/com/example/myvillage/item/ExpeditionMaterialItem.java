package com.example.myvillage.item;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class ExpeditionMaterialItem extends Item {
    private final String id;
    public ExpeditionMaterialItem(Properties props,String id){super(props);this.id=id;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);lines.add(Component.translatable("tooltip.myvillage.wilderness."+id).withStyle(net.minecraft.ChatFormatting.GRAY));}
}
