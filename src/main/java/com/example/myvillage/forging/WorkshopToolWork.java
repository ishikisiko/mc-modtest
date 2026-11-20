package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class WorkshopToolWork {
    private static final Map<UUID, BreakCapture> CAPTURING = new HashMap<>();

    record BreakResult(boolean broken, List<ItemEntity> drops) {
        private static final BreakResult REFUSED = new BreakResult(false, List.of());
    }

    private static final class BreakCapture {
        final BlockPos pos;
        BlockDropsEvent event;

        BreakCapture(BlockPos pos) { this.pos = pos; }
    }

    private WorkshopToolWork() {}

    static boolean accessible(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
    }

    static BreakResult breakWithDrops(ServerPlayer player, BlockPos pos, BlockState expected) {
        ServerLevel level = player.serverLevel();
        if (!accessible(player, pos) || level.getBlockState(pos) != expected) return BreakResult.REFUSED;
        var capture = new BreakCapture(pos);
        if (CAPTURING.putIfAbsent(player.getUUID(), capture) != null) return BreakResult.REFUSED;
        try {
            if (!player.gameMode.destroyBlock(pos) || level.getBlockState(pos) == expected) return BreakResult.REFUSED;
            var event = capture.event;
            if (event == null || event.isCanceled()) return new BreakResult(true, List.of());
            var dropped = event.getDrops().stream().filter(entity -> entity.isAlive()
                    && level.getEntity(entity.getUUID()) == entity && !entity.getItem().isEmpty()).toList();
            return new BreakResult(true, dropped);
        } finally {
            CAPTURING.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void collectDrops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player) {
            var capture = CAPTURING.get(player.getUUID());
            if (capture != null && event.getLevel() == player.serverLevel() && event.getPos().equals(capture.pos))
                capture.event = event;
        }
    }

    static boolean placeOne(ServerPlayer player, BlockPos pos, ItemStack stack) {
        var level = player.serverLevel();
        if (!(stack.getItem() instanceof BlockItem block) || !accessible(player, pos)
                || !level.getBlockState(pos).isAir()) return false;
        BlockPos below = pos.below();
        var hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, .5, 0), Direction.UP, below, false);
        var single = stack.copyWithCount(1);
        var context = new UseOnContext(level, player, InteractionHand.MAIN_HAND, single, hit);
        return new BlockPlaceContext(context).getClickedPos().equals(pos) && single.useOn(context).consumesAction()
                && level.getBlockState(pos).is(block.getBlock());
    }

    static void consumeDrop(ItemEntity entity, int amount) {
        int left = entity.getItem().getCount() - amount;
        if (left <= 0) entity.discard();
        else entity.setItem(entity.getItem().copyWithCount(left));
    }
}
