package com.example.myvillage.manor;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

/** 静态雕构与实心材质一起组成完整建筑，无显示实体或逐格 BlockEntity。 */
public final class ManorComponentBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SHAPE=IntegerProperty.create("shape",0,31);
    private final ManorCatalog.Bank bank;
    private final MapCodec<ManorComponentBlock> codec;
    public ManorComponentBlock(ManorCatalog.Bank bank,BlockBehaviour.Properties properties) {
        super(properties);this.bank=bank;this.codec=simpleCodec(p->new ManorComponentBlock(bank,p));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SHAPE,0));
    }
    @Override protected MapCodec<? extends Block> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,SHAPE);}
    private VoxelShape shape(BlockState state,int kind){return bank.profile(state.getValue(SHAPE)).shape(kind,state.getValue(FACING));}
    public ManorCatalog.Profile profile(BlockState state){return bank.profile(state.getValue(SHAPE));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state,0);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state,1);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return shape(state,2);}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player) {
        int shape=state.getValue(SHAPE);var stack=new ItemStack(asItem());
        stack.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("shape",Integer.toString(shape))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(shape+1));return stack;
    }
}
