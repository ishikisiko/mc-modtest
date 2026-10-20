package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** Vanilla tool mechanics, with server-only work receipts and shared forging presentation. */
public final class WorkshopTools {
    private WorkshopTools(){}
    private static Component name(Component base,ItemStack stack){return base.copy().withStyle(s->s.withColor(ForgingQualityDisplay.color(ForgingQualityDisplay.quality(stack))));}
    private static void tooltip(ItemStack stack,List<Component> lines){
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());
        lines.add(ForgingQualityDisplay.name(ForgingQualityDisplay.quality(stack)));
        if(mark!=null)lines.add(Component.translatable("forging.myvillage.trait."+mark.trait()));
        WorkshopEquipment.tooltip(stack,lines);
    }
    private static boolean reequips(ItemStack old,ItemStack next){
        var a=old.copy();var b=next.copy();
        a.remove(ModDataComponents.TEMPER.get());b.remove(ModDataComponents.TEMPER.get());
        a.setDamageValue(0);b.setDamageValue(0);return !ItemStack.isSameItemSameComponents(a,b);
    }
    public static final class Pickaxe extends PickaxeItem implements WorkshopTool {
        public Pickaxe(Properties properties){super(Tiers.IRON,properties);}
        @Override public Component getName(ItemStack stack){return name(super.getName(stack),stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);tooltip(stack,lines);}
        @Override public boolean shouldCauseReequipAnimation(ItemStack old,ItemStack next,boolean changed){return reequips(old,next);}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity miner){
            boolean result=super.mineBlock(stack,level,state,pos,miner);
            if(result&&!stack.isEmpty())WorkshopProgress.mined(stack,level,state,pos,miner);return result;
        }
    }
    public static final class Axe extends AxeItem implements WorkshopTool {
        public Axe(Properties properties){super(Tiers.IRON,properties);}
        @Override public Component getName(ItemStack stack){return name(super.getName(stack),stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);tooltip(stack,lines);}
        @Override public boolean shouldCauseReequipAnimation(ItemStack old,ItemStack next,boolean changed){return reequips(old,next);}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity miner){
            boolean result=super.mineBlock(stack,level,state,pos,miner);
            if(result&&!stack.isEmpty())WorkshopProgress.mined(stack,level,state,pos,miner);return result;
        }
    }
    public static final class Shovel extends ShovelItem implements WorkshopTool {
        public Shovel(Properties properties){super(Tiers.IRON,properties);}
        @Override public Component getName(ItemStack stack){return name(super.getName(stack),stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);tooltip(stack,lines);}
        @Override public boolean shouldCauseReequipAnimation(ItemStack old,ItemStack next,boolean changed){return reequips(old,next);}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity miner){
            boolean result=super.mineBlock(stack,level,state,pos,miner);
            if(result&&!stack.isEmpty())WorkshopProgress.mined(stack,level,state,pos,miner);return result;
        }
    }
    public static final class Hoe extends HoeItem implements WorkshopTool {
        public Hoe(Properties properties){super(Tiers.IRON,properties);}
        @Override public Component getName(ItemStack stack){return name(super.getName(stack),stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);tooltip(stack,lines);}
        @Override public boolean shouldCauseReequipAnimation(ItemStack old,ItemStack next,boolean changed){return reequips(old,next);}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity miner){
            boolean result=super.mineBlock(stack,level,state,pos,miner);
            if(result&&!stack.isEmpty())WorkshopProgress.mined(stack,level,state,pos,miner);return result;
        }
        @Override public InteractionResult useOn(UseOnContext context){
            var before=context.getLevel().getBlockState(context.getClickedPos());var stack=context.getItemInHand();
            var result=super.useOn(context);
            if(!before.is(Blocks.FARMLAND)&&context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.FARMLAND)&&!stack.isEmpty())
                WorkshopProgress.tilled(stack,context.getLevel(),context.getClickedPos(),context.getPlayer());
            return result;
        }
    }
}
