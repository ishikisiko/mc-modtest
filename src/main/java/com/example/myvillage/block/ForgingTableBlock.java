package com.example.myvillage.block;

import com.example.myvillage.forging.ForgingMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Workbench-style menus keep no shared or hidden inventory in the block. */
public final class ForgingTableBlock extends Block {
    public static final MapCodec<ForgingTableBlock> CODEC=simpleCodec(ForgingTableBlock::new);
    public ForgingTableBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p)p.openMenu(new SimpleMenuProvider(
                (id,inventory,viewer)->new ForgingMenu(id,inventory,ContainerLevelAccess.create(level,pos)),
                Component.translatable("block.myvillage.forging_table")));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
