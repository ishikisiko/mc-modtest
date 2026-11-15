package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

public final class WorkpieceItem extends Item {
    private final String recipe;private final int stage;
    public WorkpieceItem(Properties properties,String recipe,int stage){super(properties.stacksTo(1));this.recipe=recipe;this.stage=stage;}
    public String recipe(){return recipe;}public int stage(){return stage;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        var piece=stack.get(ModDataComponents.WORKPIECE.get());
        lines.add(Component.translatable("workshop.yuanxian.stage."+stage).withStyle(net.minecraft.ChatFormatting.GOLD));
        if(piece!=null)lines.add(Component.translatable("workshop.yuanxian.formed",piece.points(),WorkshopRules.get().formingPoints(piece.recipe())).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("workshop.yuanxian.resume").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
