package com.example.myvillage.forging;

import com.example.myvillage.item.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import java.util.List;

public final class FireCaptureItem extends Item {
    public FireCaptureItem(Properties properties){super(properties.stacksTo(16));}
    public static ItemStack seed(String kind,int maturity,String dimension,BlockPos origin){
        var item=ModItems.FOUNDRY_ITEMS.get(kind+"_fire_seed");if(item==null)throw new IllegalArgumentException("Unknown fire");
        var stack=new ItemStack(item.get());stack.set(ModDataComponents.FIRE_SEED.get(),new FireSeed(kind,maturity,dimension,origin.asLong()));return stack;
    }
    private boolean receive(ServerPlayer player,ItemStack bottle,String kind,BlockPos pos){
        if(player.getCooldowns().isOnCooldown(this)||!player.isAlive()||player.isSpectator())return false;
        var result=seed(kind,0,player.level().dimension().location().toString(),pos);
        bottle.shrink(1);player.getInventory().placeItemBackInInventory(result);player.getInventory().setChanged();
        player.getCooldowns().addCooldown(this,FoundryRules.get().fire().capture_cooldown_ticks());
        player.displayClientMessage(Component.translatable("foundry.myvillage.captured",FoundryRules.get().flame(kind).name()),true);return true;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        String kind=state.getBlock() instanceof AuxiliaryFireSourceBlock source&&state.getValue(AuxiliaryFireSourceBlock.LIT)?source.kind():
                state.is(Blocks.LIGHTNING_ROD)&&state.getValue(LightningRodBlock.POWERED)?"thunder":null;
        if(kind==null)return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(context.getPlayer() instanceof ServerPlayer player&&player.canInteractWithBlock(pos,0)
                &&receive(player,context.getItemInHand(),kind,pos)){
            if(state.getBlock() instanceof AuxiliaryFireSourceBlock)level.setBlock(pos,state.setValue(AuxiliaryFireSourceBlock.LIT,false),3);
            else level.setBlock(pos,state.setValue(LightningRodBlock.POWERED,false),3);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(!(target instanceof Blaze)||target.getPersistentData().getBoolean("myvillage_fire_captured"))return InteractionResult.PASS;
        if(player.level().isClientSide())return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer owner)||!target.isAlive()||owner.distanceToSqr(target)>25||!com.example.myvillage.combat.runtime.CombatActors.mayHarm(owner,target))return InteractionResult.FAIL;
        if(target.getHealth()>target.getMaxHealth()*FoundryRules.get().fire().capture_health_fraction()){
            owner.displayClientMessage(Component.translatable("foundry.myvillage.capture_weak"),true);return InteractionResult.FAIL;
        }
        if(receive(owner,stack,"beast",target.blockPosition())){target.getPersistentData().putBoolean("myvillage_fire_captured",true);return InteractionResult.CONSUME;}
        return InteractionResult.FAIL;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);lines.add(Component.translatable("foundry.myvillage.capture_hint"));}
}
