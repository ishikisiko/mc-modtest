package com.example.myvillage.item;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.entity.flight.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.tribulation.TribulationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RideableFlyingSwordItem extends Item {
    private final boolean azure;
    public RideableFlyingSwordItem(Properties properties) { this(properties, false); }
    public RideableFlyingSwordItem(Properties properties, boolean azure) { super(properties); this.azure = azure; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer p))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        if (p.getVehicle() instanceof RideableFlyingSwordEntity sword && sword.isOwnedBy(p)) {
            sword.requestLanding(); return InteractionResultHolder.success(stack);
        }
        if (FlyingSwordInventory.occupied(p)) {
            RideableFlyingSwordEntity.recallOwned(p); return InteractionResultHolder.success(stack);
        }
        FlyingSwordRules rules = FlyingSwordRules.load(p);
        if (!eligible(p, rules)) return refuse(p, stack, "需觉醒并达到御剑境界（默认筑基），且须在干燥地面、非渡劫时唤剑。");
        if (!p.isCreative() && CultivationService.getProfile(p).currentSpiritualPower() < rules.takeoff_cost()+rules.cruise_cost())
            return refuse(p, stack, "灵力不足以起飞并维持飞剑。");
        RideableFlyingSwordEntity sword = ModEntities.RIDEABLE_FLYING_SWORD.get().create(serverLevel);
        if (sword == null) return InteractionResultHolder.fail(stack);
        sword.moveTo(p.getX(), p.getY()+.05, p.getZ(), p.getYRot(), 0);
        var box = sword.getBoundingBox();
        if (!serverLevel.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),
                    net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                || !serverLevel.noBlockCollision(sword, sword.getBoundingBox())
                || !serverLevel.getWorldBorder().isWithinBounds(sword.getBoundingBox())) {
            sword.discard(); return refuse(p, stack, "周围或头顶空间不足，无法唤剑。");
        }
        sword.initializeFlight(p, azure, rules);
        p.setShiftKeyDown(false);
        if (!serverLevel.addFreshEntity(sword) || !p.startRiding(sword, true)) {
            sword.discard(); return InteractionResultHolder.fail(stack);
        }
        if (!p.isCreative() && !CultivationService.spendSpiritualPower(p, rules.takeoff_cost())) {
            sword.discard(); return InteractionResultHolder.fail(stack);
        }
        FlyingSwordInventory.take(p, stack);
        p.displayClientMessage(Component.literal("御剑：WASD 平移，空格升，潜行降，疾跑催剑；空手右键缓降收剑。"), true);
        return InteractionResultHolder.success(stack);
    }
    private static boolean eligible(ServerPlayer p, FlyingSwordRules r) {
        if (!FlyingSwordAuthority.mayLaunch(p.isAlive(), p.isSpectator(), p.isPassenger(), p.isSleeping(),
                p.onGround(), p.isInWaterOrBubble() || p.isInLava(), TribulationManager.isActive(p), true)) return false;
        if (p.isCreative()) return true;
        var profile = CultivationService.getProfile(p);
        if (r.require_awakened() && !profile.awakened()) return false;
        var realms = p.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS);
        var minimum = realms.get(ResourceLocation.parse(r.minimum_realm()));
        var current = realms.get(profile.realmId());
        return minimum != null && current != null && current.hasStage(profile.stageId()) && current.sortOrder() >= minimum.sortOrder();
    }
    private static InteractionResultHolder<ItemStack> refuse(ServerPlayer p, ItemStack stack, String text) {
        p.displayClientMessage(Component.literal(text), true); return InteractionResultHolder.fail(stack);
    }
}
