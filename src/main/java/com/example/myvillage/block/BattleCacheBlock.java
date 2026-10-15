package com.example.myvillage.block;

import com.example.myvillage.ruins.BattleCacheBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Immovable structure cache. No block item, dropped contents, loot table or automation capability. */
public final class BattleCacheBlock extends Block implements EntityBlock {
    public static final MapCodec<BattleCacheBlock> CODEC=simpleCodec(BattleCacheBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public BattleCacheBlock(BlockBehaviour.Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LIT);}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(1,0,1,15,15,15);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new BattleCacheBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
        return l.isClientSide || type!=ModBlockEntities.BATTLE_CACHE.get()?null:(level,pos,state,be)->((BattleCacheBlockEntity)be).tick();
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof BattleCacheBlockEntity cache)cache.use(server);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand==InteractionHand.MAIN_HAND && player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof BattleCacheBlockEntity cache)cache.use(server);
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }
}
