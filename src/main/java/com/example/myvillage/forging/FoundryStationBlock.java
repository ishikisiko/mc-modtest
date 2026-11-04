package com.example.myvillage.forging;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class FoundryStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    private static final MapCodec<FoundryStationBlock> KILN_CODEC=simpleCodec(p->new FoundryStationBlock(p,FoundryMenu.Kind.KILN));
    private static final MapCodec<FoundryStationBlock> ASSEMBLY_CODEC=simpleCodec(p->new FoundryStationBlock(p,FoundryMenu.Kind.ASSEMBLY));
    private final FoundryMenu.Kind kind;
    public FoundryStationBlock(Properties properties,FoundryMenu.Kind kind){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(LIT,false));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return kind==FoundryMenu.Kind.KILN?KILN_CODEC:ASSEMBLY_CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return kind==FoundryMenu.Kind.KILN?Block.box(0,0,0,16,16,16):Block.box(0,0,0,16,14,16);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return kind==FoundryMenu.Kind.KILN?new HeatingKilnBlockEntity(pos,state):new AssemblyBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        if(level.isClientSide())return null;
        var expected=kind==FoundryMenu.Kind.KILN?com.example.myvillage.block.ModBlockEntities.HEATING_KILN.get():com.example.myvillage.block.ModBlockEntities.ASSEMBLY_TABLE.get();
        return type==expected?(world,pos,current,entity)->((FoundryBlockEntity)entity).serverTick():null;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return ItemInteractionResult.FAIL;
        if(player.isShiftKeyDown())return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level.isClientSide())return ItemInteractionResult.SUCCESS;
        if(player instanceof ServerPlayer p&&level.getBlockEntity(pos) instanceof FoundryBlockEntity block){
            if(block instanceof HeatingKilnBlockEntity kiln)kiln.transfer(p,hand);else p.openMenu(block);
        }
        return ItemInteractionResult.CONSUME;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isSpectator()||!player.isAlive())return InteractionResult.FAIL;
        if(player instanceof ServerPlayer p&&level.getBlockEntity(pos) instanceof FoundryBlockEntity block){
            if(block instanceof HeatingKilnBlockEntity kiln&&!player.isShiftKeyDown()&&!kiln.getItem(0).isEmpty())kiln.transfer(p,InteractionHand.MAIN_HAND);
            else p.openMenu(block);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof FoundryBlockEntity block)block.dropContents();super.onRemove(state,level,pos,next,moved);}
}
