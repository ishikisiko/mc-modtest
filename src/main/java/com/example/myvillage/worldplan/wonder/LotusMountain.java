package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.wilderness.habitat.HabitatLayout;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/** One seed-selected wild landmark. Neither siting nor placement loads or writes neighbouring chunks. */
public final class LotusMountain {
    private static final long SITE_SALT = 0x4c4f5455534d544eL;
    private static final int TERRAIN_SAMPLE_STEP = 16;

    public record Rules(String region, int siteRadius, int attempts, int clearance, int relief, String sha256) {
        public Rules {
            if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("石莲山地形摘要无效");
        }
    }

    private record Column(LotusMountainTerrain.Profile profile, int originY, int fromY) {
        int topY() { return profile.surfaceY() + originY; }
        BlockState block(int y, LotusMountainTerrain terrain) {
            return profile.block(y - originY, terrain.bedY, terrain.palette);
        }
    }

    private final int originX, originZ, baseY;
    private final LotusMountainTerrain terrain;
    private final Column[] columns;
    private final BoundingBox bounds;
    private final int width;

    private LotusMountain(int originX, int originZ, int baseY, LotusMountainTerrain terrain,
                          int[] nativeHeights, int surfaceSeal, int minY, int maxY) {
        this.originX = originX;
        this.originZ = originZ;
        this.baseY = baseY;
        this.terrain = terrain;
        bounds = new BoundingBox(originX + terrain.minX, minY, originZ + terrain.minZ,
            originX + terrain.maxX, maxY - 1, originZ + terrain.maxZ);
        width = bounds.getXSpan();
        columns = new Column[nativeHeights.length];
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int i = index(x, z);
                var profile = terrain.profile(x - originX, z - originZ);
                if (profile == null) continue;
                int nativeY = nativeHeights[i];
                int shift = Math.round((nativeY - baseY - profile.groundY()) * (profile.blend() / 255f));
                int y = baseY + shift;
                int from = Math.min(y + terrain.bedY, nativeY - surfaceSeal);
                if (profile.blend() > 0) {
                    int blendedGround = y + profile.groundY();
                    from = Math.max(from, Math.min(nativeY, blendedGround) - surfaceSeal);
                }
                columns[i] = new Column(profile, y, Math.max(minY, from));
            }
        }
    }

    public static Optional<LotusMountain> plan(WorldPlan world, RoadLayout roads, RoadRules roadRules,
                                               StarterMineGeneration mines, HabitatLayout habitats,
                                               int minY, int maxY) {
        Rules rules = world.rules().lotusMountain;
        var terrain = LotusMountainTerrain.load(rules.sha256());
        var region = world.regions().stream().filter(r -> r.id().equals(rules.region())).findFirst().orElseThrow();
        var center = world.geometry().unwarp(region.x(), region.z());
        var random = new SplittableRandom(world.seed() ^ SITE_SALT);
        int spawnRadius = world.rules().spawnPadRadius + world.rules().spawnSkirt;
        int habitatRadius = habitats.rules().integer("site_radius");
        for (int i = 0; i < rules.attempts(); i++) {
            double angle = random.nextDouble() * 2 * StrictMath.PI;
            double distance = StrictMath.sqrt(random.nextDouble()) * rules.siteRadius();
            int x = (int)Math.round(center[0] + distance * StrictMath.cos(angle));
            int z = (int)Math.round(center[1] + distance * StrictMath.sin(angle));
            int x0 = x + terrain.minX - rules.clearance();
            int x1 = x + terrain.maxX + rules.clearance();
            int z0 = z + terrain.minZ - rules.clearance();
            int z1 = z + terrain.maxZ + rules.clearance();
            if (overlaps(x0, z0, x1, z1, world.spawn().x(), world.spawn().z(), spawnRadius)) continue;
            if (world.graph().geometry().reservations().stream().anyMatch(r ->
                x0 <= r.maxX() && x1 >= r.minX() && z0 <= r.maxZ() && z1 >= r.minZ())) continue;
            if (habitats.sites().stream().anyMatch(s -> overlaps(x0, z0, x1, z1, s.x(), s.z(), habitatRadius))) continue;
            if (roads.intersects(world, roadRules, x0, minY, z0, x1, maxY, z1)
                || mines.intersects(x0, minY, z0, x1, maxY, z1)) continue;

            int left = x + terrain.minX, right = x + terrain.maxX;
            int back = z + terrain.minZ, front = z + terrain.maxZ;
            int base = baseElevation(world, region, left, right, back, front, rules.relief());
            if (base == Integer.MIN_VALUE || base + terrain.bedY < minY || base + terrain.topY >= maxY) continue;
            int[] heights = new int[(right - left + 1) * (front - back + 1)];
            if (checkFootprint(world, region, terrain, x, z, base, rules.relief(), heights)) {
                return Optional.of(new LotusMountain(x, z, base, terrain, heights, world.rules().surfaceSeal, minY, maxY));
            }
        }
        return Optional.empty();
    }

    private static boolean overlaps(int x0, int z0, int x1, int z1, int x, int z, int radius) {
        return x0 <= x + radius && x1 >= x - radius && z0 <= z + radius && z1 >= z - radius;
    }

    private static int baseElevation(WorldPlan world, WorldPlan.Region region, int x0, int x1, int z0, int z1, int relief) {
        int[] samples = new int[((x1 - x0) / TERRAIN_SAMPLE_STEP + 1) * ((z1 - z0) / TERRAIN_SAMPLE_STEP + 1)];
        int i = 0;
        for (int z = z0; z <= z1; z += TERRAIN_SAMPLE_STEP) {
            for (int x = x0; x <= x1; x += TERRAIN_SAMPLE_STEP) {
                if (world.coreRegionAt(x, z).orElse(null) != region) return Integer.MIN_VALUE;
                samples[i++] = world.surfaceY(x, z);
            }
        }
        Arrays.sort(samples);
        if (samples[0] <= world.rules().seaLevel || samples[samples.length - 1] - samples[0] > relief) {
            return Integer.MIN_VALUE;
        }
        // The preview's outer foot is roughly ten blocks above its reference plane.
        return samples[samples.length / 2] - 10;
    }

    private static boolean checkFootprint(WorldPlan world, WorldPlan.Region region, LotusMountainTerrain terrain,
                                          int originX, int originZ, int base, int relief, int[] heights) {
        int i = 0;
        for (int z = terrain.minZ; z <= terrain.maxZ; z++) {
            for (int x = terrain.minX; x <= terrain.maxX; x++, i++) {
                if (terrain.profile(x, z) == null) continue;
                int worldX = originX + x, worldZ = originZ + z;
                if (world.coreRegionAt(worldX, worldZ).orElse(null) != region) return false;
                int height = world.surfaceY(worldX, worldZ);
                if (height <= world.rules().seaLevel || Math.abs(height - (base + 10)) > relief) return false;
                heights[i] = height;
            }
        }
        return true;
    }

    public BlockPos arena() {
        return new BlockPos(originX + terrain.arenaX, baseY + terrain.arenaY, originZ + terrain.arenaZ);
    }

    public boolean intersects(int x0, int z0, int x1, int z1) {
        return bounds.intersects(x0, z0, x1, z1);
    }

    private int index(int x, int z) {
        return (z - bounds.minZ()) * width + x - bounds.minX();
    }

    private Column column(int x, int z) {
        return bounds.intersects(x, z, x, z) ? columns[index(x, z)] : null;
    }

    public boolean reserved(int x, int y, int z) {
        Column column = column(x, z);
        return column != null && y >= column.fromY();
    }

    public int height(int x, int z, Heightmap.Types type, int originalHeight) {
        Column column = column(x, z);
        if (column == null) return originalHeight;
        Predicate<BlockState> opaque = type.isOpaque();
        for (int y = column.topY() - 1; y >= column.fromY(); y--) {
            if (opaque.test(column.block(y, terrain))) return y + 1;
        }
        return Math.min(column.fromY(), originalHeight);
    }

    public void replaceColumn(int x, int z, int minY, BlockState[] states) {
        Column column = column(x, z);
        if (column == null) return;
        for (int i = Math.max(0, column.fromY() - minY); i < states.length; i++) {
            states[i] = column.block(i + minY, terrain);
        }
    }

    public void place(ChunkAccess chunk) {
        var pos = chunk.getPos();
        if (!intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ())) return;
        var at = new BlockPos.MutableBlockPos();
        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                Column column = column(x, z);
                if (column == null) continue;
                int original = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15) + 1;
                int top = Math.min(chunk.getMaxBuildHeight(), Math.max(original, column.topY()));
                for (int y = column.fromY(); y < top; y++) {
                    at.set(x, y, z);
                    BlockState block = column.block(y, terrain);
                    if (chunk.getBlockState(at) != block) chunk.setBlockState(at, block, false);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }
}
