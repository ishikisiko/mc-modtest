package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.boss.lair.LairProgress;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.beast.LotusGuardEntity;
import com.example.myvillage.ruins.BattleReceipts;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One persistent encounter at the uniquely planned arena. Unloading never vacates the slot. */
public final class LotusArenaEncounter extends SavedData {
    private static final String SAVE_ID = "yuanxian_lotus_arena";
    private LairProgress progress = new LairProgress("READY", null);
    private BlockPos arena;

    private static LotusArenaEncounter load(CompoundTag tag, HolderLookup.Provider registries) {
        var encounter = new LotusArenaEncounter();
        encounter.progress = new LairProgress(tag.getString("State"), tag.hasUUID("Boss") ? tag.getUUID("Boss") : null);
        if (tag.contains("Arena")) encounter.arena = BlockPos.of(tag.getLong("Arena"));
        return encounter;
    }

    private static LotusArenaEncounter get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(LotusArenaEncounter::new, LotusArenaEncounter::load), SAVE_ID);
    }

    public static String status(ServerLevel level, BlockPos site) {
        var data = get(level);
        return data.arena != null && !data.arena.equals(site) ? "unavailable"
            : data.progress.state().name().toLowerCase(Locale.ROOT);
    }

    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        var rules = LotusGuardEntity.rules().arena();
        if (level.getGameTime() % rules.checkTicks() != 0) return;
        WorldPlanRuntime.generator(level).flatMap(g -> g.lotusMountain()).ifPresent(mountain ->
                get(level).update(level, mountain.arena()));
    }

    private void update(ServerLevel level, BlockPos home) {
        if (arena != null && !arena.equals(home)) return;
        var receipts = BattleReceipts.get(level);
        if (progress.state() == LairProgress.State.ACTIVE) {
            var id = progress.boss();
            switch (receipts.status(id)) {
                case "dead" -> { progress.died(id); receipts.forget(id); setDirty(); }
                case "retry" -> { progress.retry(id); receipts.forget(id); setDirty(); }
                default -> { }
            }
        }
        if (progress.state() != LairProgress.State.READY || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;

        var site = Vec3.atBottomCenterOf(home);
        var rule = LotusGuardEntity.rules().arena();
        double radiusSquared = Mth.square(rule.wakeRadius());
        boolean playerNear = level.players().stream().anyMatch(player -> LotusGuardEntity.isFighter(player)
            && Math.abs(player.getY() - site.y) <= rule.elevation() && player.distanceToSqr(site) <= radiusSquared);
        if (!playerNear || !level.isPositionEntityTicking(home) || !level.areEntitiesLoaded(ChunkPos.asLong(home))) return;

        LotusGuardEntity boss = ModEntities.LOTUS_GUARD.get().create(level);
        if (boss == null) return;
        boss.moveTo(site.x, site.y, site.z, 0, 0); // Faces the southern steps.
        if (!hasSpace(level, boss, home)) return;
        boss.bindArena(home);
        boss.addTag("lotus_mountain_guard");
        var id = boss.getUUID();
        if (!progress.bind(id)) return;
        receipts.register(id);
        if (level.addFreshEntity(boss)) {
            arena = home;
            setDirty();
        } else {
            progress.retry(id);
            receipts.forget(id);
        }
    }

    private static boolean hasSpace(ServerLevel level, LotusGuardEntity boss, BlockPos home) {
        var box = boss.getBoundingBox();
        var at = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1e-6); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1e-6); z++) {
                at.set(x, home.getY() - 1, z);
                if (!level.hasChunkAt(at) || !level.getBlockState(at).isCollisionShapeFullBlock(level, at)) return false;
            }
        }
        return level.getWorldBorder().isWithinBounds(box) && !level.containsAnyLiquid(box) && level.noCollision(boss, box);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("State", progress.state().name());
        if (progress.boss() != null) tag.putUUID("Boss", progress.boss());
        if (arena != null) tag.putLong("Arena", arena.asLong());
        return tag;
    }
}
