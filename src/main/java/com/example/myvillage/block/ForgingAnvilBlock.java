package com.example.myvillage.block;

import com.example.myvillage.forging.WorkshopMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

/** Ordinary-survival workshop. The open menu owns private, return-on-close workpieces. */
public final class ForgingAnvilBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ForgingAnvilBlock> CODEC=simpleCodec(ForgingAnvilBlock::new);
    public ForgingAnvilBlock(Properties props){super(props);registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider(
                (id,inventory,viewer)->new WorkshopMenu(id,inventory,ContainerLevelAccess.create(level,pos)),
                Component.translatable("workshop.myvillage.title")));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
