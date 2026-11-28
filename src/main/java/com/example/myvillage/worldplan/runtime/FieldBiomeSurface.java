package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.FieldBiomeLayout;
import com.example.myvillage.worldplan.ProvincialBiomeRules.Fluid;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites.Site;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import static com.example.myvillage.region.runtime.RegionGeometry.noise;

/** Surfacing runs after biome surface rules and before the authored town and mine layers. */
final class FieldBiomeSurface {
    private static final Map<List<String>, List<BlockState>> PALETTES = new ConcurrentHashMap<>();
    private static final BlockState[] WIND_LAYERS = {
        Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
        Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()
    };

    private FieldBiomeSurface() {}

    static void apply(FieldBiomeLayout field, ChunkAccess chunk) {
        if (field == null) return;
        var world = field.world();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int sea = world.rules().seaLevel;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int wx = x0 + x, wz = z0 + z;
            var ground = field.surface(wx,wz);
            var domain = ground.encounter();
            if (ground.biome() == null) continue;
            int top = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (domain != null && domain.kind() == Kind.TIDE && top < sea - world.rules().fieldBiomes.tide.maximumBiomeDepth()) continue;
            double grain = noise(world.seed() ^ 4019L, wx / 13., wz / 14.);
            double drift = noise(world.seed() ^ 6087L, wx / 41., wz / 48.);
            var provincial = ground.provincial();
            if (provincial != null) {
                surfaceProvince(chunk, pos, wx, wz, top, ground, grain, drift);
                continue;
            }
            for (int j = 0; j < 4 && top - j >= chunk.getMinBuildHeight(); j++) {
                pos.set(wx, top - j, wz);
                if (!chunk.getBlockState(pos).getFluidState().isEmpty()) continue;
                var state = domain.kind() == Kind.TIDE ? tide(top, sea, j, grain, drift)
                        : wind(top, sea, j, grain, drift);
                chunk.setBlockState(pos, state, false);
            }
        }
    }

    private static void surfaceProvince(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int top,
                                        FieldBiomeLayout.Column ground, double grain, double drift) {
        var terrain = ground.provincial().terrain();
        var palette = PALETTES.computeIfAbsent(terrain.palette(), names -> names.stream().map(name -> {
            var id = ResourceLocation.parse(name);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("未知州域地表方块 " + id);
            return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }).toList());
        for (int depth = 0; depth < 4 && top - depth >= chunk.getMinBuildHeight(); depth++) {
            pos.set(x, top - depth, z);
            if (!chunk.getBlockState(pos).getFluidState().isEmpty()) continue;
            double bands = grain * .55 + drift;
            int layer = depth > 1 ? 3 : bands > .51 ? 2 : depth == 1 || bands < -.22 ? 1 : 0;
            chunk.setBlockState(pos, palette.get(layer), false);
        }
        if (terrain.fluid() == Fluid.ICE && drift + grain * .25 > -.17 && ground.liquidY() - 1 > top) {
            pos.set(x, ground.liquidY() - 1, z);
            if (chunk.getFluidState(pos).is(FluidTags.WATER)) chunk.setBlockState(pos, Blocks.PACKED_ICE.defaultBlockState(), false);
        }
        if (terrain.fluid() == Fluid.SEA && ground.liquidY() - top >= 4 && ground.liquidY() - top <= 23 && drift > .12) {
            pos.set(x, top + 1, z);
            if (chunk.getFluidState(pos).is(FluidTags.WATER) && grain > .25) {
                BlockState coral = drift > .42 ? Blocks.BRAIN_CORAL_BLOCK.defaultBlockState() : Blocks.TUBE_CORAL_BLOCK.defaultBlockState();
                chunk.setBlockState(pos, coral, false);
                pos.move(0, 1, 0);
                if (chunk.getFluidState(pos).is(FluidTags.WATER) && grain > .48)
                    chunk.setBlockState(pos, Blocks.HORN_CORAL.defaultBlockState(), false);
            }
        }
    }

    static void placeMarkers(Collection<Site> sites, ChunkAccess chunk) {
        for (var site : sites) {
            if (site.domain().kind() != Kind.TIDE) continue;
            BlockPos gate = site.marker();
            for (int dx = -2; dx <= 2; dx++) {
                BlockPos at = gate.offset(dx, 0, 0);
                if (!chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(at))) continue;
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, at.getX() & 15, at.getZ() & 15);
                BlockPos sill = at.atY(floor);
                BlockPos fixture = sill.above();
                if (Math.abs(fixture.getY() - gate.getY()) > 1 || !chunk.getBlockState(fixture).isAir()) continue;
                if (dx == 0 && fixture.equals(gate)) {
                    chunk.setBlockState(sill, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), false);
                    chunk.setBlockState(gate, Blocks.LEVER.defaultBlockState()
                            .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR), false);
                } else if (dx != 0) {
                    chunk.setBlockState(sill, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), false);
                    if (Math.abs(dx) == 2)
                        chunk.setBlockState(fixture, Blocks.DARK_OAK_FENCE.defaultBlockState(), false);
                }
            }
        }
    }

    private static BlockState tide(int top, int sea, int depth, double grain, double drift) {
        if (top <= sea + 4) {
            if (depth > 1) return grain > .3 ? Blocks.CLAY.defaultBlockState() : Blocks.STONE.defaultBlockState();
            if (drift + grain * .47 > .16) return Blocks.MUD.defaultBlockState();
            return grain > -.3 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        }
        if (top > sea + 19 && drift > .03) return depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        return grain + drift * .6 > .08 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static BlockState wind(int top, int sea, int depth, double grain, double drift) {
        if (depth == 0 && top <= sea + 29 && grain + drift > -.39) return Blocks.RED_SAND.defaultBlockState();
        int stratum = Math.floorDiv(top - depth + (int)(drift * 5), 5);
        return WIND_LAYERS[Math.floorMod(stratum, WIND_LAYERS.length)];
    }
}
