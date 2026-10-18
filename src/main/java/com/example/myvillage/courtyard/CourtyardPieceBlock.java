package com.example.myvillage.courtyard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

/** One ordinary placeable block, never an entity or an overhanging invisible full cube. */
public final class CourtyardPieceBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<CourtyardPieceBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            Codec.STRING.fieldOf("piece").forGetter(b->b.piece.id()),propertiesCodec()).apply(instance,CourtyardPieceBlock::new));
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MIRRORED=BooleanProperty.create("mirrored");
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private final CourtyardCatalog.Piece piece;

    public CourtyardPieceBlock(String id, BlockBehaviour.Properties properties) {
        super(properties);piece=CourtyardCatalog.get(id);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MIRRORED,false).setValue(WATERLOGGED,false));
    }
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,MIRRORED,WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED,!piece.full() && context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context){return piece.shape(state.getValue(FACING),state.getValue(MIRRORED));}
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        if(mirror==Mirror.NONE)return state;
        int turn=CourtyardCatalog.turn(state.getValue(FACING));
        int result=mirror==Mirror.FRONT_BACK?-turn:2-turn;
        return state.setValue(FACING,CourtyardCatalog.facing(result)).cycle(MIRRORED);
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player) {
        var stack=new ItemStack(asItem());
        // Preserve the selected sculpture's handedness; placement still chooses a new facing.
        if(state.getValue(MIRRORED))stack.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("mirrored","true")));
        return stack;
    }
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return !piece.full() && SimpleWaterloggedBlock.super.canPlaceLiquid(player,level,pos,state,fluid);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        if(state.getValue(WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
}
