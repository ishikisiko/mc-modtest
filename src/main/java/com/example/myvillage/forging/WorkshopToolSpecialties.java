package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.SpiritCropBlock;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.item.ModDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class WorkshopToolSpecialties {
    private static final String TEXT = "workshop.yuanxian.specialty.";
    private static final TagKey<Block> SOUNDING_ROCK = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("yuanxian", "workshop_ore_sounding_rock"));
    private static final Map<UUID, GradeSelection> GRADING = new HashMap<>();

    private record GradeSelection(ItemStack tool, int height) {}

    private WorkshopToolSpecialties() {}

    static boolean has(ItemStack stack, String specialty) {
        if (!(stack.getItem() instanceof WorkshopTool)) return false;
        var mark = stack.get(ModDataComponents.FORGE_MARK.get());
        return mark != null && mark.trait().equals(specialty);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void use(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK
                || event.getHand() != InteractionHand.MAIN_HAND || event.getFace() == null) return;
        var player = event.getPlayer();
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !player.getAbilities().mayBuild) return;
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorkshopTool) || stack != player.getMainHandItem()) return;
        var mark = stack.get(ModDataComponents.FORGE_MARK.get());
        if (mark == null) return;
        var pos = event.getPos();
        if (!(player instanceof ServerPlayer) && !event.getLevel().isClientSide) return;
        if (player instanceof ServerPlayer server && !WorkshopToolWork.accessible(server, pos)) return;
        var state = event.getLevel().getBlockState(pos);
        boolean handles = switch (mark.trait()) {
            case ForgeMark.SEEK_VEIN -> stack.getItem() instanceof WorkshopTools.Pickaxe
                    && player.isShiftKeyDown() && state.is(SOUNDING_ROCK);
            case ForgeMark.REPLANT_TREE -> stack.getItem() instanceof WorkshopTools.Axe
                    && player.isShiftKeyDown() && state.is(BlockTags.LOGS);
            case ForgeMark.GRADE_GROUND -> stack.getItem() instanceof WorkshopTools.Shovel
                    && player.isShiftKeyDown() && event.getFace() == Direction.UP && state.is(WorkshopGrading.SOILS);
            case ForgeMark.HARVEST_HERBS -> stack.getItem() instanceof WorkshopTools.Hoe
                    && state.getBlock() instanceof SpiritCropBlock crop && crop.isMaxAge(state);
            default -> false;
        };
        if (!handles) return;

        // Run before ItemStack.useOn opens NeoForge's block snapshot transaction.
        if (player instanceof ServerPlayer server) {
            switch (mark.trait()) {
                case ForgeMark.SEEK_VEIN -> seek(server, stack, pos, event.getFace(), event.getUseOnContext().getClickLocation(), mark);
                case ForgeMark.REPLANT_TREE -> {
                    if (!WorkshopChainHarvest.fellAndReplant(server, pos, state)) message(server, "tree_unavailable");
                }
                case ForgeMark.GRADE_GROUND -> grade(server, stack, pos, mark);
                case ForgeMark.HARVEST_HERBS -> harvestHerbs(server, stack, pos, mark);
                default -> throw new IllegalStateException("Unhandled tool specialty " + mark.trait());
            }
        }
        event.cancelWithResult(ItemInteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    private static void seek(ServerPlayer player, ItemStack stack, BlockPos origin, Direction face,
                             Vec3 hit, ForgeMark mark) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        var family = stack.get(ModDataComponents.LAST_MINED_ORE_FAMILY.get());
        if (family == null || !WorkshopChainConfig.oreFamilies().contains(family.toString())) {
            message(player, "seek_sample");
            return;
        }
        var rules = WorkshopSpecialtyRules.get();
        if (!CultivationService.spendSpiritualPower(player, rules.seekQi())) {
            message(player, "seek_qi");
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), rules.seekCooldown());
        var level = player.serverLevel();
        var tag = TagKey.create(Registries.BLOCK, family);
        var ore = findOre(origin, face.getOpposite(), (int) value(mark),
                pos -> WorkshopToolWork.accessible(player, pos) && level.getBlockState(pos).is(tag));
        if (ore == null) {
            message(player, "seek_empty");
            return;
        }
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 direction = Vec3.atCenterOf(ore).subtract(Vec3.atCenterOf(origin));
        Vec3 surfaceDirection = direction.subtract(normal.scale(direction.dot(normal)));
        Vec3 start = hit.add(normal.scale(.13));
        Vec3 lightDirection = surfaceDirection.lengthSqr() == 0 ? normal : surfaceDirection.normalize();
        for (int i = 0; i < 6; i++) {
            var spot = start.add(lightDirection.scale(i * .11));
            level.sendParticles(player, ParticleTypes.END_ROD, false, spot.x, spot.y, spot.z, 1, 0, 0, 0, .005);
        }
        message(player, surfaceDirection.lengthSqr() == 0 ? "seek_direct" : "seek_found");
    }

    static BlockPos findOre(BlockPos origin, Direction into, int radius, Predicate<BlockPos> ore) {
        int limit = radius * radius;
        double bestDistance = limit + 1;
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                                                   origin.offset(radius, radius, radius))) {
            double distance = origin.distSqr(pos);
            if (distance >= bestDistance || distance > limit) continue;
            int inward = (pos.getX() - origin.getX()) * into.getStepX()
                    + (pos.getY() - origin.getY()) * into.getStepY()
                    + (pos.getZ() - origin.getZ()) * into.getStepZ();
            if (inward <= 0 || !ore.test(pos)) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    private static void grade(ServerPlayer player, ItemStack stack, BlockPos pos, ForgeMark mark) {
        var selected = GRADING.remove(player.getUUID());
        if (selected == null || selected.tool() != stack) {
            GRADING.put(player.getUUID(), new GradeSelection(stack, pos.getY()));
            message(player, "grade_height", pos.getY());
            return;
        }
        var result = WorkshopGrading.grade(player, stack, pos, selected.height(), (int) value(mark),
                WorkshopSpecialtyRules.get().gradeDepth());
        if (result.cut() + result.filled() == 0) message(player, "grade_empty");
        else message(player, "grade_result", result.cut(), result.filled());
    }

    private static void harvestHerbs(ServerPlayer player, ItemStack tool, BlockPos pos, ForgeMark mark) {
        var level = player.serverLevel();
        var mature = level.getBlockState(pos);
        var crop = (SpiritCropBlock) mature.getBlock();
        var growing = crop.getStateForAge(crop.getMaxAge() - 1);
        if (!growing.canSurvive(level, pos)) return;
        boolean instantBreak = mature.getDestroySpeed(level, pos) == 0;
        var result = WorkshopToolWork.breakWithDrops(player, pos, mature);
        if (!result.broken()) return;
        if (instantBreak && !tool.isEmpty()) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        if (!level.getBlockState(pos).isAir() || !growing.canSurvive(level, pos)
                || !level.setBlockAndUpdate(pos, growing)) return;
        var rules = WorkshopSpecialtyRules.get();
        int remaining = rules.herbsRetained();
        var herb = crop.asItem();
        for (var drop : result.drops()) {
            if (remaining == 0 || !drop.isAlive() || !drop.getItem().is(herb)) continue;
            int taken = Math.min(remaining, drop.getItem().getCount());
            WorkshopToolWork.consumeDrop(drop, taken);
            remaining -= taken;
        }
        if (remaining == 0 && level.random.nextDouble() < value(mark)) {
            Block.popResource(level, pos, new ItemStack(herb, rules.herbsBonus()));
            message(player, "herbs_bonus", rules.herbsBonus());
        }
    }

    static double value(ForgeMark mark) {
        return ForgingRules.get().trait(mark.trait()).value(mark.quality());
    }

    private static void message(ServerPlayer player, String id, Object... values) {
        player.displayClientMessage(Component.translatable(TEXT + id, values), true);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var selected = GRADING.get(player.getUUID());
            if (selected != null && (selected.tool() != player.getMainHandItem() || !player.isAlive())) clear(player.getUUID());
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { GRADING.clear(); }

    private static void clear(UUID id) { GRADING.remove(id); }
}
