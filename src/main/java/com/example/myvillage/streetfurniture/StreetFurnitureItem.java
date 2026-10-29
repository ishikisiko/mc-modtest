package com.example.myvillage.streetfurniture;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;

/** 一次检查所有占用格，再由服务器写入、发声并消耗一件物品。 */
public final class StreetFurnitureItem extends BlockItem {
    private final StreetFurnitureBlock furniture;
    public StreetFurnitureItem(StreetFurnitureBlock block,Properties properties){super(block,properties);furniture=block;}

    @Override public InteractionResult place(BlockPlaceContext context){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();
        if(player==null||!context.canPlace()||!furniture.isEnabled(level.enabledFeatures()))return InteractionResult.FAIL;
        var family=furniture.family();Direction facing=context.getHorizontalDirection().getOpposite();BlockPos root=context.getClickedPos();
        List<BlockPos> positions=new ArrayList<>();List<BlockState> before=new ArrayList<>(),after=new ArrayList<>();
        for(int i=0;i<family.parts().size();i++){
            BlockPos pos=root.offset(family.offset(i,facing));
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)
                    ||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
            var old=level.getBlockState(pos);var local=BlockPlaceContext.at(context,pos,context.getClickedFace());
            if(!old.canBeReplaced(local)||level.getBlockEntity(pos)!=null)return InteractionResult.FAIL;
            if(family.part(i).offset().getY()==0&&!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return InteractionResult.FAIL;
            var state=furniture.state(i,facing,level.getFluidState(pos).getType()==Fluids.WATER);
            if(!level.isUnobstructed(state,pos,CollisionContext.of(player)))return InteractionResult.FAIL;
            positions.add(pos);before.add(old);after.add(state);
        }
        if(level.isClientSide())return InteractionResult.SUCCESS;
        boolean completed=false;int written=0;StreetFurnitureBlock.beginEdit();
        try{
            for(int i=0;i<positions.size();i++){
                if(!level.setBlock(positions.get(i),after.get(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return InteractionResult.FAIL;
                written++;
            }
            completed=true;
        }finally{
            if(!completed)for(int i=written-1;i>=0;i--)level.setBlock(positions.get(i),before.get(i),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            StreetFurnitureBlock.endEdit();
        }
        for(BlockPos pos:positions)level.updateNeighborsAt(pos,furniture);
        var rootState=after.getFirst();var sound=rootState.getSoundType(level,root,player);
        level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE,root,GameEvent.Context.of(player,rootState));
        if(player instanceof ServerPlayer server)CriteriaTriggers.PLACED_BLOCK.trigger(server,root,stack);
        stack.consume(1,player);return InteractionResult.CONSUME;
    }
}
