package com.example.myvillage.block;

import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import com.example.myvillage.sim.runtime.player.ScriptureHall;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 经架: the scripture hall's shelf. A plain full cube with a {@link ScriptureShelfBlockEntity}
 * holding the owning sect ({@code Block implements EntityBlock}, not {@code BaseEntityBlock},
 * whose render shape would default to invisible). Using it with an empty hand asks the server's
 * {@link ScriptureHall} to open the hall for the player; the client only draws what comes back.
 */
public final class ScriptureShelfBlock extends Block implements EntityBlock {
    public static final MapCodec<ScriptureShelfBlock> CODEC = simpleCodec(ScriptureShelfBlock::new);

    public ScriptureShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptureShelfBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            ScriptureHall.open(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
