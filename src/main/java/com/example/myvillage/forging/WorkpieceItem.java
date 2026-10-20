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
        lines.add(Component.translatable("workshop.myvillage.stage."+stage).withStyle(net.minecraft.ChatFormatting.GOLD));
        if(piece!=null)lines.add(Component.translatable("workshop.myvillage.formed",piece.blows(),WorkshopRules.get().forming_strikes()).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("workshop.myvillage.resume").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
