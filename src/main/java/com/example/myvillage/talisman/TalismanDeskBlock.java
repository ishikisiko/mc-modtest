package com.example.myvillage.talisman;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class TalismanDeskBlock extends Block implements EntityBlock {
    public static final MapCodec<TalismanDeskBlock> CODEC = simpleCodec(TalismanDeskBlock::new);
    public TalismanDeskBlock(BlockBehaviour.Properties p) { super(p); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) { return box(1, 0, 1, 15, 12, 15); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new TalismanDeskBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type) {
        return l.isClientSide() || type != TalismanContent.DESK_ENTITY.get() ? null
                : (w, p, state, e) -> TalismanDeskBlockEntity.tick(w, p, state, (TalismanDeskBlockEntity) e);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult h) {
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof TalismanDeskBlockEntity desk) {
            if (desk.occupied()) server.displayClientMessage(net.minecraft.network.chat.Component.literal("此案正有人使用"), true);
            else server.openMenu(desk);
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected List<ItemStack> getDrops(BlockState s, LootParams.Builder params) {
        // One sealed desk contains inventory AND paid draft. Never also spill its slots.
        ItemStack stack = new ItemStack(TalismanContent.DESK_ITEM.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TalismanDeskBlockEntity desk)
            desk.saveToItem(stack, params.getLevel().registryAccess());
        return List.of(stack);
    }
}
