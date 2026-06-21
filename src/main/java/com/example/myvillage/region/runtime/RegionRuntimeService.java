package com.example.myvillage.region.runtime;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side region runtime: caches the per-seed region graph for the overworld
 * and binds world spawn once, deterministically, to the lowest-tier eligible
 * region. The runtime is <b>passive</b> — it reads the world seed, computes and
 * caches the graph, answers queries, and calls {@code setSpawnPos} exactly once
 * per world. It writes no terrain, overrides no biome, and hooks no chunk-gen.
 *
 * <p><b>Lifecycle.</b> On {@link ServerStartedEvent} the service loads the
 * shipped ruleset + catalog via the server's {@code ResourceManager}, generates
 * the graph from the overworld seed, caches it, and performs the one-time
 * spawn binding. On {@link ServerStoppingEvent} the cache is cleared.
 *
 * <p><b>Spawn binding policy.</b> The binding runs at most once per world
 * (gated by {@link RegionRuntimeState#spawnBound()}). On the first encounter,
 * if the world's spawn is still the vanilla-default sentinel (origin column
 * {@code x == 0 && z == 0}), the runtime computes the spawn region, runs a
 * safe-surface search, and calls {@code setSpawnPos}. If the spawn is already
 * non-default (admin-set via {@code /setworldspawn} or otherwise), the runtime
 * <b>does not override</b> it and records the existing spawn as the bound
 * state so the decision is never revisited.
 */
public final class RegionRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionRuntimeService.class);

    private static volatile RegionGraph cachedGraph;
    private static volatile RegionSpawnSelector.SpawnSelection cachedSpawn;
    private static volatile List<Integer> cachedLadder;
    private static volatile MinecraftServer cachedServer;

    private RegionRuntimeService() {
    }

    /**
     * Handle server start: load + cache the overworld graph and bind spawn once.
     */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        cachedServer = server;
        try {
            RegionCatalogLoader.RegionData data = RegionCatalogLoader.loadFromResourceManager(
                    server.getResourceManager(), MyVillageMod.MOD_ID);
            long seed = overworld.getSeed();
            RegionGraph graph = RegionTopologyGenerator.generate(seed, data.ruleset(), data.catalog());
            cachedGraph = graph;
            cachedSpawn = RegionSpawnSelector.select(graph).orElse(null);
            cachedLadder = RungLadder.ladder(graph);
            LOGGER.info("Region runtime loaded for seed {}: {} regions, {} rungs, spawn region={}",
                    seed, graph.regions().size(), cachedLadder.size(),
                    cachedSpawn == null ? "<none eligible>" : cachedSpawn.regionId());
            bindSpawnOnce(overworld);
        } catch (RuntimeException ex) {
            // The runtime is passive: a load failure must not crash the server.
            // The region layer is offline-only until the runtime succeeds.
            LOGGER.error("Region runtime failed to load; region layer is inactive this session", ex);
            cachedGraph = null;
            cachedSpawn = null;
            cachedLadder = null;
        }
    }

    /** Clear cached state on server stop. */
    public static void onServerStopping(ServerStoppingEvent event) {
        cachedGraph = null;
        cachedSpawn = null;
        cachedLadder = null;
        cachedServer = null;
    }

    /**
     * The cached overworld region graph, or empty if the runtime has not loaded
     * (e.g. before server start, or after a load failure).
     */
    public static Optional<RegionGraph> graph() {
        return Optional.ofNullable(cachedGraph);
    }

    /**
     * The cached tier-rung ladder (ascending distinct non-walled tiers), or
     * empty if the runtime is inactive.
     */
    public static Optional<List<Integer>> ladder() {
        return Optional.ofNullable(cachedLadder);
    }

    /**
     * The per-world persistent spawn-binding audit, or empty if no server is
     * running. Read by the {@code /myvillage spawn info} query.
     */
    public static Optional<RegionRuntimeState> state() {
        ServerLevel overworld = currentOverworld();
        return overworld == null ? Optional.empty() : Optional.of(RegionRuntimeState.get(overworld));
    }

    /**
     * The computed spawn selection (region id + world block), or empty if the
     * runtime is inactive or no region is eligible.
     */
    public static Optional<RegionSpawnSelector.SpawnSelection> spawnSelection() {
        return Optional.ofNullable(cachedSpawn);
    }

    /**
     * Resolve the region at a world block. Empty if the runtime is inactive or
     * the point is outside the bounded area.
     */
    public static Optional<String> regionAt(int worldX, int worldZ) {
        RegionGraph graph = cachedGraph;
        return graph == null ? Optional.empty() : RegionQueries.regionAt(graph, worldX, worldZ);
    }

    /**
     * The region the player currently stands in (via {@code region_at} over the
     * player's block position). Empty if the runtime is inactive or the player
     * is outside the bounded area.
     */
    public static Optional<String> currentRegion(ServerPlayer player) {
        RegionGraph graph = cachedGraph;
        if (graph == null) {
            return Optional.empty();
        }
        BlockPos p = player.blockPosition();
        return RegionQueries.regionAt(graph, p.getX(), p.getZ());
    }

    /**
     * The rung (tier value) the player is currently on. Empty if the runtime
     * is inactive, the player is outside the bounded area, or the player is in
     * a walled region (魔域, off the ladder by construction).
     */
    public static Optional<Integer> currentRung(ServerPlayer player) {
        RegionGraph graph = cachedGraph;
        if (graph == null) {
            return Optional.empty();
        }
        BlockPos p = player.blockPosition();
        return RungLadder.currentRung(graph, p.getX(), p.getZ());
    }

    /**
     * The <b>set</b> of non-walled regions at the next-higher rung above the
     * player's current rung. Empty if the player is at the top rung (中州),
     * outside the bounded area, or in a walled region. The result is a set
     * because tier ties are branch points (resolved later by the alignment
     * system).
     */
    public static Set<String> nextRungRegions(ServerPlayer player) {
        RegionGraph graph = cachedGraph;
        if (graph == null) {
            return Set.of();
        }
        BlockPos p = player.blockPosition();
        return RungLadder.nextRungRegions(graph, p.getX(), p.getZ());
    }

    /**
     * Force a spawn recompute for the current world (admin command path,
     * {@code /myvillage spawn recompute}). Recomputes the spawn block from the
     * cached selection, calls {@code setSpawnPos} (overriding any existing
     * spawn — this is the documented admin-override semantics), and re-marks
     * the per-world state bound. Returns false if the runtime is inactive or
     * no spawn region is eligible.
     */
    public static boolean recomputeSpawn() {
        ServerLevel overworld = currentOverworld();
        if (overworld == null) {
            return false;
        }
        RegionSpawnSelector.SpawnSelection sel = cachedSpawn;
        if (sel == null) {
            return false;
        }
        BlockPos block = resolveSpawnBlock(overworld, sel);
        overworld.setDefaultSpawnPos(block, 0.0f);
        RegionRuntimeState.get(overworld).markSpawnBound(sel.regionId(), block);
        LOGGER.info("Region runtime: recomputed spawn to {} (region {})", block, sel.regionId());
        return true;
    }

    private static void bindSpawnOnce(ServerLevel overworld) {
        RegionRuntimeState state = RegionRuntimeState.get(overworld);
        if (state.spawnBound()) {
            // Already made the one-time decision for this world.
            return;
        }
        RegionSpawnSelector.SpawnSelection selection = cachedSpawn;
        if (selection == null) {
            // No eligible region — nothing to bind; mark bound so we don't retry every load.
            state.markSpawnBound("<none>", overworld.getSharedSpawnPos());
            return;
        }
        BlockPos existing = overworld.getSharedSpawnPos();
        if (!isDefaultSpawn(existing)) {
            // Existing custom (admin-set or vanilla-worldgen-placed) spawn — preserve it.
            LOGGER.info("Region runtime: preserving existing spawn {} (non-default); not overriding",
                    existing);
            state.markSpawnBound(selection.regionId(), existing);
            return;
        }
        BlockPos block = resolveSpawnBlock(overworld, selection);
        overworld.setDefaultSpawnPos(block, 0.0f);
        LOGGER.info("Region runtime: bound spawn to {} (region {})", block, selection.regionId());
        state.markSpawnBound(selection.regionId(), block);
    }

    private static BlockPos resolveSpawnBlock(
            ServerLevel overworld, RegionSpawnSelector.SpawnSelection selection) {
        var found = SpawnSurfaceSearch.findStandable(overworld, selection.worldX(), selection.worldZ());
        if (found.isPresent()) {
            return found.get();
        }
        // Fallback: vanilla setworldspawn semantics — heightmap surface at the region center.
        return SpawnSurfaceSearch.heightmapSurface(overworld, selection.worldX(), selection.worldZ());
    }

    /**
     * The "vanilla-default spawn" sentinel: the origin column {@code x==0, z==0}.
     * A spawn here is treated as unset/default and safe for the runtime to
     * replace; any other location is treated as an existing (admin or
     * worldgen-placed) spawn to preserve on first load.
     */
    private static boolean isDefaultSpawn(BlockPos pos) {
        return pos.getX() == 0 && pos.getZ() == 0;
    }

    private static ServerLevel currentOverworld() {
        MinecraftServer server = cachedServer;
        return server == null ? null : server.overworld();
    }
}
