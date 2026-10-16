package com.example.myvillage.item;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.ruins.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** A bounded server transaction: validate ground, create one saved trial, then consume one pearl. */
public final class DemonLurePearlItem extends Item {
    public DemonLurePearlItem(Properties p){super(p.stacksTo(DemonTrialRules.get().stackSize()));}
    private InteractionResult reject(ServerPlayer player,String key,Object... args){
        player.displayClientMessage(Component.translatable("message.myvillage.demon_trial."+key,args),true);
        return InteractionResult.FAIL;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.FAIL;
        var level=player.serverLevel();var config=DemonTrialRules.get();var stack=context.getItemInHand();
        if(!player.isAlive() || player.isSpectator() || !player.mayBuild() || stack.isEmpty())return reject(player,"permission");
        if(player.getCooldowns().isOnCooldown(this))return InteractionResult.FAIL;
        if(level.getDifficulty()==Difficulty.PEACEFUL)return reject(player,"peaceful");
        if(context.getClickedFace()!=Direction.UP)return reject(player,"ground");
        BlockPos core=context.getClickedPos().above();int radius=config.radius();
        if(!level.getWorldBorder().isWithinBounds(core.offset(-radius,0,-radius))
                || !level.getWorldBorder().isWithinBounds(core.offset(radius,0,radius))
                || level.isOutsideBuildHeight(core.below()) || level.isOutsideBuildHeight(core.above(config.height()-1)))
            return reject(player,"ground");
        if(!level.mayInteract(player,core) || !player.mayUseItemAt(core,Direction.UP,stack))return reject(player,"permission");
        int gap=config.separation();
        if(!level.hasChunksAt(core.offset(-gap,0,-gap),core.offset(gap,0,gap)))return reject(player,"loading");
        // Search loaded chunk block entities, not an unbounded world-wide list or a cubic block scan.
        for(int cx=(core.getX()-gap)>>4;cx<=(core.getX()+gap)>>4;cx++)
            for(int cz=(core.getZ()-gap)>>4;cz<=(core.getZ()+gap)>>4;cz++)
                for(var be:level.getChunk(cx,cz).getBlockEntities().values())
                    if(be instanceof BattleCacheBlockEntity && be.getBlockPos().distSqr(core)<gap*gap)
                        return reject(player,"nearby",gap);
        for(var floor:BlockPos.betweenClosed(core.offset(-radius,-1,-radius),core.offset(radius,-1,radius))){
            if(!level.getBlockState(floor).isCollisionShapeFullBlock(level,floor) || !level.getFluidState(floor).isEmpty())
                return reject(player,"space",radius*2+1,config.height());
            for(int dy=1;dy<=config.height();dy++){
                var p=floor.above(dy);
                if(!level.getBlockState(p).isAir() || !level.getFluidState(p).isEmpty())return reject(player,"space",radius*2+1,config.height());
            }
        }
        if(!level.noCollision(new AABB(core)))return reject(player,"occupied");
        var state=ModBlocks.DEMON_TRIAL_CORE.get().defaultBlockState();
        if(!level.setBlock(core,state,Block.UPDATE_ALL))return reject(player,"ground");
        if(!(level.getBlockEntity(core) instanceof BattleCacheBlockEntity trial) || !trial.summonTrial()){
            level.removeBlock(core,false);return reject(player,"ground");
        }
        if(!player.getAbilities().instabuild)stack.shrink(1);
        player.getCooldowns().addCooldown(this,config.cooldownTicks());
        level.sendParticles(ParticleTypes.SOUL,core.getX()+.5,core.getY()+.8,core.getZ()+.5,24,.5,.5,.5,0);
        level.playSound(null,core,SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.BLOCKS,.7F,.7F);
        player.displayClientMessage(Component.translatable("message.myvillage.demon_trial.start"),true);
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        var r=DemonTrialRules.get();
        lines.add(Component.translatable("tooltip.myvillage.demon_lure_pearl",r.radius()*2+1,r.height()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.demon_lure_pearl.reward").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
