package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.block.SpiritCropBlock;
import com.example.myvillage.block.SpiritSoilBlock;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.profession.PhysicalGardenWork;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/** Server-thread bridge from an earned life action to one explicitly bound, loaded crop block. */
public final class NpcGardenRuntime {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NpcGardenRuntime.class);
    private NpcGardenRuntime() { }

    public static boolean bind(ServerLevel level, WorldSim sim, String siteId, String placeId,
                               String herb, BlockPos cropPos) {
        var site = sim.lifeSite(siteId).orElseThrow(() -> new NpcLifeSites.SiteException("unknown_site", siteId));
        if (!site.dimension().equals(level.dimension().location().toString()))
            throw new NpcLifeSites.SiteException("wrong_dimension");
        if (observeAge(level, cropPos, herb) == -2)
            throw new NpcLifeSites.SiteException("physical_garden_invalid", cropPos.toShortString());
        return sim.bindPhysicalGarden(siteId, placeId, herb, cropPos.getX(), cropPos.getY(), cropPos.getZ());
    }

    /** Unknown/unloaded observations clear eligibility; they never recreate or mature a block. */
    public static boolean observe(MinecraftServer server, WorldSim sim) {
        boolean changed = false;
        for (var site : sim.lifeSites()) {
            ServerLevel level = NpcLifeSites.level(server, site);
            var supply = sim.professionSupply(site.id()).orElseThrow();
            for (var garden : supply.gardens()) if (garden.physical()) {
                BlockPos pos = new BlockPos(garden.cropX(), garden.cropY(), garden.cropZ());
                changed |= sim.observePhysicalGarden(site.id(), garden.placeId(),
                        level == null ? -2 : observeAge(level, pos, garden.herb()));
            }
        }
        return changed;
    }

    public static boolean completeReadyWork(MinecraftServer server, WorldSim sim) {
        boolean changed = false;
        for (var life : sim.lifeResidents()) {
            var work = sim.physicalGardenWork(life.personId(), life.epoch());
            if (work.isEmpty()) continue;
            var body = WorldSimAvatars.managedLifeEntity(life.personId());
            if (body.isPresent()) changed |= complete(sim, body.get(), work.get());
        }
        return changed;
    }

    private static boolean complete(WorldSim sim, NpcEntity npc, PhysicalGardenWork work) {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        BlockPos pos = new BlockPos(work.cropX(), work.cropY(), work.cropZ());
        var site = sim.lifeSite(work.siteId()).orElse(null);
        LifePlace station = site == null ? null : site.place(work.placeId()).orElse(null);
        if (station == null || !site.dimension().equals(level.dimension().location().toString())
                || !actorCanWork(sim, npc, work, station, level, pos)) return false;
        if (observeAge(level, pos, work.herb()) != work.expectedAge()) return false;
        BlockState before = level.getBlockState(pos);
        SpiritCropBlock crop = crop(work.herb());
        if (crop == null) return false;
        int units = 0;
        if (work.activity().equals("harvest")) {
            var table = level.getServer().reloadableRegistries().getLootTable(crop.getLootTable());
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, before)
                    .withParameter(LootContextParams.THIS_ENTITY, npc).create(LootContextParamSets.BLOCK);
            // A full warehouse or replaced worker cannot reroll this harvest until it actually commits.
            long seed = harvestSeed(level.getSeed() ^ level.dimension().location().toString().hashCode(),
                    pos.asLong(), work.harvestSerial());
            units = harvestUnits(table.getRandomItems(params, seed), herb(work.herb()), sim.lifeRules().maxInventory());
            if (units <= 0) return false;
        }
        int credited = units;
        boolean committed = sim.commitPhysicalGardenWork(work, units, () -> {
            if (!actorCanWork(sim, npc, work, station, level, pos)
                    || level.getBlockState(pos) != before || observeAge(level, pos, work.herb()) != work.expectedAge()) return false;
            return switch (work.activity()) {
                case "plant" -> before.isAir() && level.setBlock(pos, crop.defaultBlockState(), Block.UPDATE_ALL);
                case "tend" -> before.getBlock() == crop && !crop.isMaxAge(before)
                        && CommonHooks.canCropGrow(level, pos, before, true)
                        && level.getBlockState(pos) == before
                        && level.setBlock(pos, crop.getStateForAge(crop.getAge(before) + 1), Block.UPDATE_ALL);
                case "harvest" -> before.getBlock() == crop && crop.isMaxAge(before)
                        && CommonHooks.canEntityDestroy(level, pos, npc)
                        && level.getBlockState(pos) == before
                        && level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                default -> false;
            };
        });
        if (!committed) return false;
        npc.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        npc.swing(InteractionHand.MAIN_HAND);
        if (work.activity().equals("plant")) {
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(npc, crop.defaultBlockState()));
        } else if (work.activity().equals("tend")) {
            CommonHooks.fireCropGrowPost(level, pos, before);
            level.levelEvent(1505, pos, 0);
        } else {
            level.levelEvent(2001, pos, Block.getId(before));
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(npc, before));
        }
        LOGGER.info("NPC_GARDEN_COMMIT person={} epoch={} revision={} action={} crop={} old_age={} units={} serial={}",
                work.personId(), work.epoch(), work.revision(), work.activity(), pos, work.expectedAge(), credited, work.harvestSerial());
        return true;
    }

    private static boolean actorCanWork(WorldSim sim, NpcEntity npc, PhysicalGardenWork work,
                                       LifePlace station, ServerLevel level, BlockPos pos) {
        if (!npc.isAlive() || npc.isRemoved() || npc.combatEngaged() || !npc.onGround()
                || !WorldSimAvatars.isManagedLife(npc, work.epoch()) || npc.ledgerPersonId() != work.personId()
                || !npc.blockPosition().equals(new BlockPos(station.x(), station.y(), station.z()))
                || npc.distanceToSqr(station.x() + .5, station.y(), station.z() + .5)
                    > sim.lifeRules().arrivalDistance() * sim.lifeRules().arrivalDistance()
                || npc.distanceToSqr(Vec3.atCenterOf(pos)) > sim.professionRules().physicalReach() * sim.professionRules().physicalReach()
                || !WorldSimAvatars.loadedArea(level, new AABB(npc.getEyePosition(), Vec3.atCenterOf(pos)).inflate(1))
                || !EventHooks.canEntityGrief(level, npc)) return false;
        var hit = level.clip(new ClipContext(npc.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    static int observeAge(ServerLevel level, BlockPos pos, String herb) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos)
                || !WorldSimAvatars.loadedArea(level, new AABB(pos).inflate(1))) return -2;
        if (!(level.getBlockState(pos.below()).getBlock() instanceof SpiritSoilBlock)) return -2;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return -1;
        SpiritCropBlock crop = crop(herb);
        return crop != null && state.getBlock() == crop ? crop.getAge(state) : -2;
    }

    private static Item herb(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static SpiritCropBlock crop(String id) {
        return herb(id) instanceof BlockItem item && item.getBlock() instanceof SpiritCropBlock crop ? crop : null;
    }

    static int harvestUnits(List<ItemStack> drops, Item herb, int limit) {
        long count = 0;
        var plain = new ItemStack(herb);
        for (var stack : drops) {
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, plain)) return -1;
            count += stack.getCount();
            if (count > limit) return -1;
        }
        return (int) count;
    }

    static long harvestSeed(long worldSeed, long blockPosition, long serial) {
        long x = worldSeed ^ Long.rotateLeft(blockPosition, 23) ^ (serial * 0x9E3779B97F4A7C15L);
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return x == 0 ? 0x9E3779B97F4A7C15L : x;
    }
}
