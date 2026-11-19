package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.worldplan.WorldPlan;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Adds lamps to open, level road edges in the chunk's completed town slice. */
final class StreetLightPlacement {
    private static final String RESOURCE = "/data/yuanxian/street_furniture/lighting.json";
    private static final int SIDE = 16;
    private static final Heightmap.Types SURFACE = Heightmap.Types.WORLD_SURFACE_WG;

    private StreetLightPlacement() {}

    private record Rules(Map<String, Set<Block>> roads, int narrow, int wide, int inset, int sky, int light) {}
    private record Site(BlockPos root, Direction inward, boolean tall, int score) {}

    private static final class Data {
        private static final Rules RULES = load();
    }

    static void place(WorldPlan plan, ChunkAccess chunk) {
        int ox = chunk.getPos().getMinBlockX(), oz = chunk.getPos().getMinBlockZ();
        var capital = plan.capitalAt(ox, oz).orElse(null);
        String profile = capital != null ? capital.regionId()
                : plan.settlementChunk(chunk.getPos().x, chunk.getPos().z).isPresent() ? "bay_terraces" : null;
        if (profile == null) return;
        Rules rules = Data.RULES;
        Set<Block> roads = rules.roads().get(profile);
        if (roads == null) return;

        Heightmap.primeHeightmaps(chunk, Set.of(SURFACE));
        int[] surface = new int[SIDE * SIDE];
        var at = new BlockPos.MutableBlockPos();
        int bottom = chunk.getMinBuildHeight();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int y = chunk.getHeight(SURFACE, x, z);
                at.set(ox + x, y, oz + z);
                var floor = chunk.getBlockState(at);
                boolean atGrade = capital == null || Math.abs(capital.surface(at.getX(), at.getZ()) - y - 1) <= 2;
                surface[z * SIDE + x] = atGrade && roads.contains(floor.getBlock())
                        && floor.isFaceSturdy(chunk, at, Direction.UP) ? y : bottom - 1;
            }
        }
        Site site = null;
        for (int z = rules.inset(); z < SIDE - rules.inset(); z++) {
            for (int x = rules.inset(); x < SIDE - rules.inset(); x++) {
                int y = surface[z * SIDE + x];
                if (y < bottom || y + rules.sky() >= chunk.getMaxBuildHeight()) continue;
                for (Direction inward : Direction.Plane.HORIZONTAL) {
                    int width = edgeWidth(surface, x, y, z, inward, rules.wide());
                    if (width < rules.narrow()) continue;
                    boolean tall = width >= rules.wide();
                    BlockPos root = new BlockPos(ox + x, y + 1, oz + z);
                    int score = Math.abs(2 * x - 15) + Math.abs(2 * z - 15) + (tall ? 0 : 4);
                    if (site != null && score >= site.score()) continue;
                    if (clear(chunk, root, inward.getOpposite(), rules)) site = new Site(root, inward, tall, score);
                }
            }
        }
        if (site == null) return;
        var lamp = site.tall() ? ModBlocks.TALL_STREET_LAMP.get() : ModBlocks.LOW_STREET_LAMP.get();
        for (int i = 0; i < lamp.family().height(); i++) {
            chunk.setBlockState(site.root().above(i), lamp.state(i, site.inward(), false), false);
        }
        Heightmap.primeHeightmaps(chunk, Set.of(SURFACE));
    }

    private static int edgeWidth(int[] surface, int x, int y, int z, Direction inward, int limit) {
        int dx = inward.getStepX(), dz = inward.getStepZ();
        if (surface[(z-dz)*SIDE+x-dx] == y || surface[(z+dx)*SIDE+x-dz] != y
                || surface[(z-dx)*SIDE+x+dz] != y) return 0;
        int width = 1;
        while (width < limit) {
            int u = x + dx * width, v = z + dz * width;
            if (u < 0 || u >= SIDE || v < 0 || v >= SIDE || surface[v*SIDE+u] != y) break;
            width++;
        }
        return width;
    }

    private static boolean clear(ChunkAccess chunk, BlockPos root, Direction outward, Rules rules) {
        if (!chunk.getBlockState(root.relative(outward)).isAir()) return false;
        var at = root.mutable();
        for (int dy = 0; dy < rules.sky(); dy++) {
            at.setY(root.getY()+dy);
            if (!chunk.getBlockState(at).isAir()) return false;
        }
        int radius = rules.light();
        for (int dx = -radius; dx <= radius; dx++) {
            int x = (root.getX() & 15) + dx;
            if (x < 0 || x >= SIDE) continue;
            for (int dz = -radius; dz <= radius; dz++) {
                int z = (root.getZ() & 15) + dz;
                if (Math.abs(dx) + Math.abs(dz) > radius || z < 0 || z >= SIDE) continue;
                for (int dy = 0; dy < rules.sky(); dy++) {
                    at.set(root.getX()+dx,root.getY()+dy,root.getZ()+dz);
                    if (chunk.getBlockState(at).getLightEmission() >= 12) return false;
                }
            }
        }
        return true;
    }

    private static Rules load() {
        try (var stream = StreetLightPlacement.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Set<Block>> roadMaterials = new HashMap<>();
            for (var region : data.getAsJsonObject("roads").entrySet()) {
                Set<Block> blocks = new HashSet<>();
                for (var value : region.getValue().getAsJsonArray()) {
                    var id = ResourceLocation.parse(value.getAsString());
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Unknown street surface " + id);
                    blocks.add(BuiltInRegistries.BLOCK.get(id));
                }
                roadMaterials.put(region.getKey(), Set.copyOf(blocks));
            }
            int narrow = data.get("narrow_road_min").getAsInt(), wide = data.get("wide_road_min").getAsInt();
            int inset = data.get("scan_inset").getAsInt(), sky = data.get("sky_clearance").getAsInt();
            int light = data.get("light_clearance").getAsInt();
            if (data.get("schema").getAsInt() != 1 || narrow < 3 || wide < narrow || wide > SIDE
                    || inset < 1 || inset > 5 || sky < 3 || sky > 8 || light < 1 || light > 8)
                throw new IllegalStateException("Invalid town street lighting rules");
            return new Rules(Map.copyOf(roadMaterials), narrow, wide, inset, sky, light);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load " + RESOURCE, e);
        }
    }
}
