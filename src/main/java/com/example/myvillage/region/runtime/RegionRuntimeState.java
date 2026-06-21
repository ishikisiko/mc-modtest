package com.example.myvillage.region.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world persistent state for the region runtime — records whether the
 * mod has already bound spawn for this world (so the binding runs
 * <em>exactly once per world</em>) and audits the computed spawn region +
 * block for the {@code /myvillage spawn info} query and the no-override policy.
 *
 * <p>The region graph itself is NOT persisted: it is recomputed deterministically
 * from the world seed on every server start, so persisting it would be
 * redundant. Only the spawn-binding audit (which is a one-time decision that
 * must not be repeated) is persisted here.
 */
public final class RegionRuntimeState extends SavedData {

    private static final String DATA_NAME = "myvillage_region_runtime";

    private boolean spawnBound;
    private String spawnRegionId;
    private BlockPos spawnBlock;

    public RegionRuntimeState() {
    }

    private RegionRuntimeState(CompoundTag tag) {
        spawnBound = tag.getBoolean("spawn_bound");
        spawnRegionId = tag.contains("spawn_region_id") ? tag.getString("spawn_region_id") : null;
        if (tag.contains("spawn_x")) {
            spawnBlock = new BlockPos(
                    tag.getInt("spawn_x"),
                    tag.getInt("spawn_y"),
                    tag.getInt("spawn_z"));
        }
    }

    /** Whether the mod has already made its one-time spawn decision for this world. */
    public boolean spawnBound() {
        return spawnBound;
    }

    /** The region id the runtime bound (or preserved) as the spawn region. */
    public String spawnRegionId() {
        return spawnRegionId;
    }

    /** The world block the runtime bound (or preserved) as the spawn point. */
    public BlockPos spawnBlock() {
        return spawnBlock;
    }

    /** Record that spawn is bound for this world and audit the chosen region + block. */
    public void markSpawnBound(String regionId, BlockPos block) {
        this.spawnBound = true;
        this.spawnRegionId = regionId;
        this.spawnBlock = block;
        setDirty();
    }

    public static RegionRuntimeState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(RegionRuntimeState::new, RegionRuntimeState::loadFromTag),
                DATA_NAME);
    }

    private static RegionRuntimeState loadFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        return new RegionRuntimeState(tag);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("spawn_bound", spawnBound);
        if (spawnRegionId != null) {
            tag.putString("spawn_region_id", spawnRegionId);
        }
        if (spawnBlock != null) {
            tag.putInt("spawn_x", spawnBlock.getX());
            tag.putInt("spawn_y", spawnBlock.getY());
            tag.putInt("spawn_z", spawnBlock.getZ());
        }
        return tag;
    }
}
