package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.block.FoldedRidgePlantBlock;
import com.example.myvillage.block.FoldedRidgeStoneBlock;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.wilderness.habitat.HabitatLayout;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/** One continuous folded rock body, anchored inside Lingyue without cross-chunk writes. */
public final class FoldedRidge {
    private static final long SITE_SALT = 0x464f4c4445445247L;
    private static final int SURVEY_STEP = 16;
    private static final Direction[] FERN_FACES = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public record Rules(String region, int siteRadius, int attempts, int clearance, int relief, int blend, String sha256) {
        public Rules {
            if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("折山岭地形摘要无效");
        }
    }

    private record Column(int profile, int from, int bottom, int top, List<FoldedRidgeTerrain.Plant> plants) {}

    private final FoldedRidgeTerrain terrain;
    private final int baseY, originX, originZ, width;
    private final BoundingBox bounds;
    private final Column[] columns;
    private final BlockState[] palette = new BlockState[20];

    private FoldedRidge(WorldPlan world, FoldedRidgeTerrain terrain, int x, int y, int z, BoundingBox bounds) {
        this.terrain = terrain;
        originX = x;
        baseY = y;
        originZ = z;
        this.bounds = bounds;
        width = bounds.getXSpan();
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 1; i <= 12; i++) {
            palette[i] = ModBlocks.FOLDED_RIDGE_STONE.get().defaultBlockState().setValue(FoldedRidgeStoneBlock.STRATUM, i);
        }
        palette[13] = ModBlocks.RIDGE_LAMP_MOSS.get().defaultBlockState();
        for (int i = 0; i < FERN_FACES.length; i++) {
            palette[i+14] = ModBlocks.RIDGE_SCALE_FERN.get().defaultBlockState().setValue(FoldedRidgePlantBlock.FACING, FERN_FACES[i]);
        }
        Map<Integer, List<FoldedRidgeTerrain.Plant>> plants = new HashMap<>();
        for (var plant : terrain.plants) {
            plants.computeIfAbsent(index(x+plant.x(), z+plant.z()), ignored -> new ArrayList<>()).add(plant);
        }
        columns = new Column[width * bounds.getZSpan()];
        for (int wz = bounds.minZ(); wz <= bounds.maxZ(); wz++) for (int wx = bounds.minX(); wx <= bounds.maxX(); wx++) {
            int i = index(wx, wz);
            int expanded = terrain.expandedIndex(wx-x, wz-z);
            if (expanded < 0) continue;
            int profile = terrain.nearest(expanded);
            if (profile < 0) continue;
            int nativeTop = world.surfaceY(wx, wz);
            int distance = terrain.edgeDistance(expanded);
            if (distance == terrain.apron) continue;
            var foliage = List.copyOf(plants.getOrDefault(i, List.of()));
            if (distance > 0) {
                double t = 1.0 - distance / (double)terrain.apron;
                double weight = t * t * (3.0 - 2.0 * t);
                int edgeTop = y + terrain.ground(profile);
                int top = nativeTop + (int)Math.round((edgeTop - nativeTop) * weight);
                columns[i] = new Column(-1, Math.max(bounds.minY(), Math.min(nativeTop, top) - world.rules().surfaceSeal),
                        top, top, foliage);
            } else {
                int bottom = y + terrain.bottom(profile);
                int from = Math.max(bounds.minY(), Math.min(nativeTop, bottom) - world.rules().surfaceSeal);
                columns[i] = new Column(profile, from, bottom, y + terrain.top(profile), foliage);
            }
        }
    }

    public static Optional<FoldedRidge> plan(WorldPlan world, RoadLayout roads, RoadRules roadRules,
                                            StarterMineGeneration mines, HabitatLayout habitats,
                                            LotusMountain lotus, int minY, int maxY) {
        Rules rules = world.rules().foldedRidge;
        var terrain = FoldedRidgeTerrain.load(rules.sha256(), rules.blend());
        var region = world.regions().stream().filter(r -> r.id().equals(rules.region())).findFirst().orElseThrow();
        double[] center = world.geometry().unwarp(region.x(), region.z());
        var random = new SplittableRandom(world.seed() ^ SITE_SALT);
        int padding = terrain.apron * terrain.step;
        for (int i = 0; i < rules.attempts(); i++) {
            double angle = random.nextDouble() * 2 * StrictMath.PI;
            double distance = StrictMath.sqrt(random.nextDouble()) * rules.siteRadius();
            int x = (int)Math.round(center[0] + distance * StrictMath.cos(angle));
            int z = (int)Math.round(center[1] + distance * StrictMath.sin(angle));
            int left = x + terrain.x0 - padding, back = z + terrain.z0 - padding;
            int right = left + terrain.width * terrain.step + 2 * padding - 1;
            int front = back + terrain.depth * terrain.step + 2 * padding - 1;
            int reliefBase = survey(world, region, left, back, right, front, rules.relief());
            if (reliefBase == Integer.MIN_VALUE) continue;
            int base = reliefBase - terrain.entry.getY();
            if (base < minY || base + terrain.peak >= maxY) continue;
            int x0 = left-rules.clearance(), z0 = back-rules.clearance();
            int x1 = right+rules.clearance(), z1 = front+rules.clearance();
            int spawn = world.rules().spawnPadRadius + world.rules().spawnSkirt;
            if (overlaps(x0, z0, x1, z1, world.spawn().x(), world.spawn().z(), spawn)
                    || world.graph().geometry().reservations().stream().anyMatch(r ->
                       x0 <= r.maxX() && x1 >= r.minX() && z0 <= r.maxZ() && z1 >= r.minZ())
                    || habitats.sites().stream().anyMatch(s ->
                       overlaps(x0, z0, x1, z1, s.x(), s.z(), habitats.rules().integer("site_radius")))
                    || (lotus != null && lotus.intersects(x0, z0, x1, z1))
                    || roads.intersects(world, roadRules, x0, minY, z0, x1, maxY, z1)
                    || mines.intersects(x0, minY, z0, x1, maxY, z1)) continue;

            boolean fits = true;
            for (int wz = back; fits && wz <= front; wz++) for (int wx = left; wx <= right; wx++) {
                if (world.coreRegionAt(wx, wz).orElse(null) != region) {
                    fits = false;
                    break;
                }
            }
            if (fits) return Optional.of(new FoldedRidge(world, terrain, x, base, z,
                    new BoundingBox(left, minY, back, right, maxY-1, front)));
        }
        return Optional.empty();
    }

    private static int survey(WorldPlan world, WorldPlan.Region region, int left, int back, int right, int front, int relief) {
        int[] samples = new int[((right-left)/SURVEY_STEP+2) * ((front-back)/SURVEY_STEP+2)];
        int n = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int z = back; ; z = Math.min(z+SURVEY_STEP, front)) {
            for (int x = left; ; x = Math.min(x+SURVEY_STEP, right)) {
                if (world.coreRegionAt(x, z).orElse(null) != region) return Integer.MIN_VALUE;
                int y = world.surfaceY(x, z);
                if (y <= world.rules().seaLevel) return Integer.MIN_VALUE;
                low = Math.min(low, y);
                high = Math.max(high, y);
                if (high-low > relief) return Integer.MIN_VALUE;
                samples[n++] = y;
                if (x == right) break;
            }
            if (z == front) break;
        }
        Arrays.sort(samples, 0, n);
        return samples[n/2];
    }

    private static boolean overlaps(int x0, int z0, int x1, int z1, int x, int z, int radius) {
        return x0 <= x+radius && x1 >= x-radius && z0 <= z+radius && z1 >= z-radius;
    }

    public BlockPos entry() { return terrain.entry.offset(originX, baseY, originZ); }
    public BlockPos origin() { return new BlockPos(originX, baseY, originZ); }
    public boolean intersects(int x0, int z0, int x1, int z1) { return bounds.intersects(x0, z0, x1, z1); }
    private int index(int x, int z) { return (z-bounds.minZ()) * width + x-bounds.minX(); }
    private Column column(int x, int z) { return intersects(x, z, x, z) ? columns[index(x, z)] : null; }

    public boolean reserved(int x, int y, int z) {
        var column = column(x, z);
        return column != null && y >= column.from;
    }

    public boolean ownsBiome(int x, int y, int z) {
        var column = column(x, z);
        // Biomes are sampled every four blocks. Include the air directly above the canopy.
        return column != null && y >= column.from && y < Math.max(column.top, highestPlant(column)) + 8;
    }

    public boolean interlayer(int x, int y, int z) {
        var column = column(x, z);
        if (column == null || column.profile < 0) return false;
        int dy = y-baseY;
        return dy >= terrain.ground(column.profile) && y+1 < column.top
                && terrain.material(column.profile, dy) == 0 && terrain.material(column.profile, dy+1) == 0;
    }

    private int highestPlant(Column column) {
        int top = Integer.MIN_VALUE;
        for (var plant : column.plants) top = Math.max(top, baseY + plant.y()+1);
        return top;
    }

    private BlockState block(Column column, int y) {
        for (var plant : column.plants) if (baseY + plant.y() == y) return palette[plant.material()];
        if (y < column.bottom) return palette[2];
        return column.profile < 0 ? palette[0] : palette[terrain.material(column.profile, y-baseY)];
    }

    public int height(int x, int z, Heightmap.Types type, int originalHeight) {
        var column = column(x, z);
        if (column == null) return originalHeight;
        for (int y = Math.max(column.top, highestPlant(column))-1; y >= column.from; y--) {
            if (type.isOpaque().test(block(column, y))) return y+1;
        }
        return Math.min(originalHeight, column.from);
    }

    public void replaceColumn(int x, int z, int minY, BlockState[] states) {
        var column = column(x, z);
        if (column == null) return;
        for (int i = Math.max(0, column.from-minY); i < states.length; i++) {
            states[i] = block(column, i+minY);
        }
    }

    public void place(ChunkAccess chunk) {
        var pos = chunk.getPos();
        if (!intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ())) return;
        var at = new BlockPos.MutableBlockPos();
        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            var column = column(x, z);
            if (column == null) continue;
            int top = Math.min(chunk.getMaxBuildHeight(), Math.max(Math.max(column.top, highestPlant(column)),
                    chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15)+1));
            for (int y = column.from; y < top; y++) {
                at.set(x, y, z);
                BlockState state = block(column, y);
                if (state != chunk.getBlockState(at)) chunk.setBlockState(at, state, false);
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }
}
