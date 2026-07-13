package com.example.myvillage.block;

import com.example.myvillage.cultivation.CultivationInitiationMessages;
import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SpiritTestingSteleBlock extends Block {
    public static final MapCodec<SpiritTestingSteleBlock> CODEC =
            simpleCodec(SpiritTestingSteleBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(4, 3, 5, 12, 15, 11),
            Block.box(3, 14, 4, 13, 16, 12));

    public SpiritTestingSteleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            SpiritualRootAwakeningService.Outcome outcome =
                    SpiritualRootAwakeningService.awaken(serverPlayer);
            CultivationInitiationMessages.awakening(serverPlayer, outcome)
                    .forEach(message -> serverPlayer.displayClientMessage(message, false));
            if (outcome.success()) {
                playSuccessEffects((ServerLevel) level, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    private static void playSuccessEffects(ServerLevel level, BlockPos pos) {
        level.playSound(
                null,
                pos,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                1.0F,
                1.0F);
        level.sendParticles(
                ParticleTypes.ENCHANT,
                pos.getX() + 0.5,
                pos.getY() + 1.1,
                pos.getZ() + 0.5,
                28,
                0.45,
                0.65,
                0.45,
                0.04);
    }
}
