package com.example.myvillage.item;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RideableFlyingSwordItem extends Item {
    private static final double FIRST_SPAWN_Y_OFFSET = 0.05;
    private static final double SPAWN_SEARCH_STEP = 0.25;
    private static final int SPAWN_SEARCH_STEPS = 4;

    public RideableFlyingSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (RideableFlyingSwordEntity.recallOwned(serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        RideableFlyingSwordEntity sword = ModEntities.RIDEABLE_FLYING_SWORD.get().create(serverLevel);
        if (sword == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (!findSpawnPosition(serverLevel, serverPlayer, sword)) {
            sword.discard();
            return InteractionResultHolder.fail(stack);
        }

        sword.bindTo(serverPlayer);
        serverPlayer.setShiftKeyDown(false);

        if (!serverLevel.addFreshEntity(sword) || !serverPlayer.startRiding(sword, true)) {
            sword.discard();
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.success(stack);
    }

    private static boolean findSpawnPosition(
            ServerLevel level,
            ServerPlayer player,
            RideableFlyingSwordEntity sword) {
        for (int step = 0; step <= SPAWN_SEARCH_STEPS; step++) {
            double y = player.getY() + FIRST_SPAWN_Y_OFFSET + step * SPAWN_SEARCH_STEP;
            sword.moveTo(player.getX(), y, player.getZ(), player.getYRot(), 0.0F);
            if (level.noBlockCollision(sword, sword.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }
}
