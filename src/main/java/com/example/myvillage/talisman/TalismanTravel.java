package com.example.myvillage.talisman;

import com.example.myvillage.cultivation.CultivationService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;

/** 两种传送符的服务器入口；可选石碑实现只经接口注入。 */
public final class TalismanTravel {
    public record WaystoneEntry(UUID id, String name, ResourceLocation dimension, BlockPos pos) { }
    public record Transfer(TravelPoint point, BooleanSupplier perform) { }
    public interface Waystones {
        List<WaystoneEntry> activated(ServerPlayer player);
        Optional<Transfer> prepare(ServerPlayer player, UUID id);
    }
    private static Waystones waystones;
    private TalismanTravel() { }
    public static void installWaystones(Waystones access) { waystones = access; }
    public static boolean waystonesAvailable() { return waystones != null; }
    public static List<WaystoneEntry> activated(ServerPlayer p) {
        return waystones == null ? List.of() : List.copyOf(waystones.activated(p));
    }
    private static boolean usable(ServerPlayer p) {
        return p.getServer() != null && p.getServer().isSameThread() && p.isAlive() && !p.isRemoved()
                && !p.isSpectator() && !p.isPassenger() && !p.isVehicle() && !p.isSleeping();
    }
    public static boolean openHome(ServerPlayer p, InteractionHand hand, int page) {
        if (!usable(p) || !p.getItemInHand(hand).is(TalismanContent.HOME.get())) return false;
        if (waystones == null) return refused(p, "回城符需要安装 Waystones 传送石碑模组");
        var entries = activated(p);
        if (entries.isEmpty()) return refused(p, "尚未激活传送石碑，请先亲自激活一块石碑");
        int pages = TalismanTravelMenu.pageCount(entries.size());
        if (page < 0 || page >= pages) return false;
        var shown = TalismanTravelMenu.page(entries, page);
        ItemStack stack = p.getItemInHand(hand);
        return p.openMenu(new SimpleMenuProvider((id, inv, viewer) ->
                        new TalismanTravelMenu(id, inv, hand, stack, shown, page, pages), Component.literal("回城符：选择石碑")),
                buffer -> TalismanTravelMenu.writePage(buffer, shown, page, pages)).isPresent();
    }
    public static boolean home(ServerPlayer p, InteractionHand hand, UUID id) {
        if (!usable(p) || !p.getItemInHand(hand).is(TalismanContent.HOME.get()) || !TalismanRuntime.ready(p)) return false;
        var route = waystones == null ? Optional.<Transfer>empty() : waystones.prepare(p, id);
        if (route.isEmpty()) return refused(p, "石碑未激活、已被拆除或落点不安全，未消耗符箓");
        return payAndTravel(p, hand, route.get());
    }
    public static boolean mark(ServerPlayer p, InteractionHand hand) {
        if (!usable(p) || !p.getItemInHand(hand).is(TalismanContent.RECALL.get())) return false;
        var point = TravelPoint.at(p);
        if (!p.onGround() || !safe(p, p.serverLevel(), point))
            return refused(p, "请站在安全的地面上标记，原标记已保留");
        p.setData(TalismanContent.RECALL_MARK.get(), p.getData(TalismanContent.RECALL_MARK.get()).replace(point));
        p.displayClientMessage(Component.literal("闪回地点已更新：" + point.dimension() + " "
                + BlockPos.containing(point.position()).toShortString() + "；标记不消耗符箓"), true);
        return true;
    }
    public static boolean recall(ServerPlayer p, InteractionHand hand) {
        if (!usable(p) || !p.getItemInHand(hand).is(TalismanContent.RECALL.get()) || !TalismanRuntime.ready(p)) return false;
        var mark = p.getData(TalismanContent.RECALL_MARK.get()).point();
        if (mark.isEmpty()) return refused(p, "尚未标记地点，请手持闪回符按 Shift＋右键标记脚下位置");
        var point = mark.get();
        var level = p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, point.dimension()));
        if (level == null || !safe(p, level, point)) return refused(p, "标记地点已被封堵或不安全，未消耗符箓");
        return payAndTravel(p, hand, new Transfer(point,
                () -> p.teleportTo(level, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch())));
    }
    private static boolean payAndTravel(ServerPlayer p, InteractionHand hand, Transfer route) {
        ItemStack stack = p.getItemInHand(hand), unit = stack.copyWithCount(1);
        boolean consumesItem = !p.getAbilities().instabuild;
        int cost = TalismanRules.get().use("activation_power");
        boolean moved = TravelPayment.execute(() -> {
            if (!usable(p) || stack.isEmpty() || p.getItemInHand(hand) != stack || !TalismanRuntime.ready(p)
                    || !CultivationService.spendSpiritualPower(p, cost)) return false;
            if (consumesItem) stack.shrink(1);
            return true;
        }, route.perform(), () -> {
            CultivationService.recoverSpiritualPower(p, cost);
            if (consumesItem) {
                if (p.getItemInHand(hand) == stack) stack.grow(1);
                else if (!p.getInventory().add(unit)) p.drop(unit, false);
            }
        }, () -> {
            TalismanRuntime.cooldown(p);
            p.fallDistance = 0;
            p.setDeltaMovement(Vec3.ZERO);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .7F, 1F);
        });
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
        if (!moved) return refused(p, "传送未完成，符箓与灵力已保留");
        p.displayClientMessage(Component.literal("已到达目的地"), true);
        return true;
    }
    /** 仅加载目的地身体覆盖的区块，不改地形；检查站立空间、液体、危险方块和脚下支撑。 */
    public static boolean safe(ServerPlayer p, ServerLevel level, TravelPoint point) {
        if (!point.valid() || !level.dimension().location().equals(point.dimension())) return false;
        var body = p.getDimensions(Pose.STANDING).makeBoundingBox(point.position());
        if (!level.getWorldBorder().isWithinBounds(body) || body.minY <= level.getMinBuildHeight()
                || body.maxY >= level.getMaxBuildHeight()) return false;
        var low = BlockPos.containing(body.minX, body.minY - 1, body.minZ);
        var high = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++)
            for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++) level.getChunk(x, z);
        for (var at : BlockPos.betweenClosed(low, high)) {
            var state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                    || state.is(Blocks.POWDER_SNOW) || CampfireBlock.isLitCampfire(state)) return false;
        }
        // Waystones chooses a point half a block above the floor; this support probe covers that offset.
        return level.noCollision(p, body) && !level.noCollision(body.move(0, -.6, 0));
    }
    private static boolean refused(ServerPlayer p, String message) {
        p.displayClientMessage(Component.literal(message), true);
        return false;
    }
}
