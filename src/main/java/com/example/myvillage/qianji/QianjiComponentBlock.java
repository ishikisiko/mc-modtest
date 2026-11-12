package com.example.myvillage.qianji;

import com.example.myvillage.manor.*;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

public final class QianjiComponentBlock extends Block implements ArchitecturalComponent {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SHAPE=IntegerProperty.create("shape",0,31);
    private final QianjiCatalog.Bank bank;
    private final MapCodec<QianjiComponentBlock> codec;
    public QianjiComponentBlock(QianjiCatalog.Bank bank,Properties p){super(p);this.bank=bank;codec=simpleCodec(v->new QianjiComponentBlock(bank,v));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SHAPE,0));}
    @Override protected MapCodec<? extends Block> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SHAPE);}
    @Override public ManorCatalog.Profile profile(BlockState s){return bank.profile(s.getValue(SHAPE));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return profile(s).shape(0,s.getValue(FACING));}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return profile(s).shape(1,s.getValue(FACING));}
    @Override protected VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p){return profile(s).shape(2,s.getValue(FACING));}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public ItemStack getCloneItemStack(BlockState s,HitResult hit,LevelReader level,BlockPos pos,Player player){var item=new ItemStack(asItem());int n=s.getValue(SHAPE);item.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("shape",Integer.toString(n))));item.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(n+1));return item;}
}
