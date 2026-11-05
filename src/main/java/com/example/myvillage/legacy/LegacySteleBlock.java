package com.example.myvillage.legacy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class LegacySteleBlock extends Block implements EntityBlock {
    public static final MapCodec<LegacySteleBlock> CODEC=simpleCodec(LegacySteleBlock::new);
    public LegacySteleBlock(BlockBehaviour.Properties p){super(p);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new LegacySteleEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||t!=LegacyContent.STELE_ENTITY.get()?null:(world,pos,state,be)->((LegacySteleEntity)be).tick();}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit){if(p instanceof ServerPlayer player&&l.getBlockEntity(pos) instanceof LegacySteleEntity be)be.use(player,InteractionHand.MAIN_HAND);return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){if(p instanceof ServerPlayer player&&l.getBlockEntity(pos) instanceof LegacySteleEntity be)be.use(player,hand);return ItemInteractionResult.sidedSuccess(l.isClientSide);}
    @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity p,ItemStack stack){super.setPlacedBy(l,pos,s,p,stack);if(!l.isClientSide&&l.getBlockEntity(pos) instanceof LegacySteleEntity be)be.ready();}
}
