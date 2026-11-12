package com.example.myvillage.qianji;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;

/** 与院落石坪同形；每座总舵只有一个动效锚点，不逐格创建方块实体。 */
public final class QianjiAtmosphereBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<QianjiAtmosphereBlock> CODEC=simpleCodec(QianjiAtmosphereBlock::new);
    public QianjiAtmosphereBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new QianjiAtmosphereEntity(p,s);}
}
