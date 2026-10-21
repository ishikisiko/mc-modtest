package com.example.myvillage.gathering;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Ordinary separately crafted pieces; no hidden entity, automatic placement or extra inventory. */
public final class GatheringOrnamentBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final String id;
    public GatheringOrnamentBlock(String id,Properties properties) {
        super(properties);this.id=id;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));
    }
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(p->new GatheringOrnamentBlock(id,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return GatheringConstruction.shape(id,state.getValue(FACING));}
}
