package com.example.myvillage.region.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

/**
 * Best-effort safe-surface search for the world spawn point — Java mirror of
 * the runtime spec's spawn step 5
 * ({@code docs/ai-kb/13_region_topology.md} "Runtime binding").
 *
 * <p>From the spawn region's placed center block, spiral outward up to
 * {@link #MAX_RADIUS} blocks and accept the first <em>standable</em> block:
 * a non-liquid surface with a clear feet and head. The spiral visits blocks
 * ring-by-ring (Chebyshev radius) in a deterministic row-major order within
 * each ring, so the chosen block is reproducible for a given world's terrain.
 *
 * <p>If no standable block is found within the cap, {@link #findStandable}
 * returns empty and the caller falls back to the region center with vanilla
 * {@code setworldspawn} semantics (the heightmap top block at the center).
 *
 * <p>Chunk generation: the search force-loads each candidate chunk via
 * {@link ServerLevel#getChunk(int, int)} so the heightmap is available; this is
 * a one-time per-world cost during spawn binding and the spiral exits early at
 * the first standable block, so the typical cost is a handful of chunks.
 */
public final class SpawnSurfaceSearch {

    /** Max Chebyshev radius (blocks) of the outward spiral from the region center. */
    public static final int MAX_RADIUS = 256;

    private SpawnSurfaceSearch() {
    }

    /**
     * Find the first standable block in an outward spiral from
     * {@code (centerX, centerZ)}.
     *
     * @return the feet position of the first standable block, or empty if none
     *         is found within {@link #MAX_RADIUS}
     */
    public static Optional<BlockPos> findStandable(ServerLevel level, int centerX, int centerZ) {
        // Center first.
        BlockPos at = standableAt(level, centerX, centerZ);
        if (at != null) {
            return Optional.of(at);
        }
        for (int r = 1; r <= MAX_RADIUS; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // ring r only
                    }
                    BlockPos candidate = standableAt(level, centerX + dx, centerZ + dz);
                    if (candidate != null) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Vanilla-fallback surface: the heightmap top block at a column, used when
     * the spiral finds nothing standable within the cap.
     */
    public static BlockPos heightmapSurface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        return new BlockPos(x, y, z);
    }

    /**
     * Returns the feet position of a standable block at {@code (x, z)}, or
     * {@code null} if the column has no standable spot at its surface.
     *
     * <p>Standable = surface block (feet-1) is non-air, non-liquid, and sturdy
     * enough to stand on; the feet (feet) and head (feet+1) blocks are
     * passable and non-liquid.
     */
    private static BlockPos standableAt(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (top < level.getMinBuildHeight() + 1) {
            return null;
        }
        int feet = top;
        BlockState surface = level.getBlockState(new BlockPos(x, feet - 1, z));
        if (surface.isAir() || !surface.getFluidState().isEmpty()) {
            return null;
        }
        if (!surface.isFaceSturdy(level, new BlockPos(x, feet - 1, z), Direction.UP)) {
            return null;
        }
        if (!isPassable(level, x, feet, z) || !isPassable(level, x, feet + 1, z)) {
            return null;
        }
        return new BlockPos(x, feet, z);
    }

    private static boolean isPassable(ServerLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        FluidState fluid = state.getFluidState();
        return state.isAir() && fluid.isEmpty();
    }
}
