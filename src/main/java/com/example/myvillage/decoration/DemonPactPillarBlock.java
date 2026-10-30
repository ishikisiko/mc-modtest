package com.example.myvillage.decoration;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

/** A continuous carved stone pillar; each occupied cell has matching model and collision. */
public final class DemonPactPillarBlock extends Block {
    public static final MapCodec<DemonPactPillarBlock> CODEC=simpleCodec(DemonPactPillarBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,DemonPactPillarShape.get().count()-1);
    private static final ThreadLocal<Integer> EDIT_DEPTH=ThreadLocal.withInitial(()->0);
    public DemonPactPillarBlock(BlockBehaviour.Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,PART); }
    BlockState state(int part, Direction facing) { return defaultBlockState().setValue(PART,part).setValue(FACING,facing); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return DemonPactPillarShape.get().part(state.getValue(PART)).shape(state.getValue(FACING)); }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return getShape(state,level,pos,context); }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type) { return false; }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return mirror==Mirror.NONE?state:state.setValue(FACING,mirror.mirror(state.getValue(FACING)))
                .setValue(PART,DemonPactPillarShape.get().mirroredPart(state.getValue(PART)));
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player) { return new ItemStack(asItem()); }
    static void beginEdit() { EDIT_DEPTH.set(EDIT_DEPTH.get()+1); }
    static void endEdit() { int next=EDIT_DEPTH.get()-1;if(next==0)EDIT_DEPTH.remove();else EDIT_DEPTH.set(next); }
    private boolean matches(BlockState candidate,BlockState original,int index) { return candidate.is(this)&&candidate.getValue(FACING)==original.getValue(FACING)&&candidate.getValue(PART)==index; }
    private void removeOthers(Level level,BlockPos pos,BlockState original,boolean drop) {
        var shape=DemonPactPillarShape.get();Direction facing=original.getValue(FACING);
        int current=original.getValue(PART);BlockPos root=pos.subtract(shape.offset(current,facing));
        // The bounded four-block footprint can straddle chunks. Load its own cells so removal never leaves ghosts.
        for(int i=0;i<shape.count();i++)level.getChunkAt(root.offset(shape.offset(i,facing)));
        beginEdit();
        try {
            if(current!=0&&matches(level.getBlockState(root),original,0)) {
                if(drop)level.destroyBlock(root,true);else level.setBlock(root,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            }
            for(int i=1;i<shape.count();i++) {
                BlockPos target=root.offset(shape.offset(i,facing));
                if(!target.equals(pos)&&matches(level.getBlockState(target),original,i))level.setBlock(target,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            }
        } finally { endEdit(); }
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!level.isClientSide()&&!level.restoringBlockSnapshots&&EDIT_DEPTH.get()==0&&!next.is(this))removeOthers(level,pos,state,true);
        super.onRemove(state,level,pos,next,moved);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide()&&player.isCreative()&&EDIT_DEPTH.get()==0)removeOthers(level,pos,state,false);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        if(EDIT_DEPTH.get()==0&&direction==Direction.DOWN&&DemonPactPillarShape.get().part(state.getValue(PART)).offset().getY()==0
                &&!neighbor.isFaceSturdy(level,neighborPos,Direction.UP))return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
}
