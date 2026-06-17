package com.example.myvillage.sect;

import com.example.myvillage.town.ModBlockFallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Single structure piece that bakes a worldgen sect: it derives the mountain
 * from the compound's terrace profile (反推山形, {@link SectMountain}) and rests
 * the shared {@link SectGenerator} realizer on it. The piece's bounding box
 * spans the whole footprint + blend skirt, so {@code postProcess} is invoked for
 * every overlapping chunk; each call writes only the cells inside that chunk's
 * region (via {@link WorldGenSink}), so the mountain survives chunk boundaries
 * with no force-load. Natural heights come from the chunk generator's base
 * height (deterministic, generation-order-independent) so every chunk slice sees
 * the same silhouette.
 */
public final class SectStructurePiece extends StructurePiece {
    private final BlockPos base;
    private final long siteSeed;

    SectStructurePiece(BlockPos base, long siteSeed) {
        super(SectStructures.SECT_PIECE.get(), 0, footprintBox(base));
        this.base = base;
        this.siteSeed = siteSeed;
    }

    public SectStructurePiece(CompoundTag tag) {
        super(SectStructures.SECT_PIECE.get(), tag);
        this.base = new BlockPos(tag.getInt("bx"), tag.getInt("by"), tag.getInt("bz"));
        this.siteSeed = tag.getLong("seed");
    }

    private static BoundingBox footprintBox(BlockPos base) {
        int margin = SectGenerator.MOUNTAIN_MARGIN;
        int x0 = base.getX() - margin;
        int z0 = base.getZ() - margin;
        int x1 = base.getX() + SectGenerator.SITE_WIDTH + margin;
        int z1 = base.getZ() + SectGenerator.SITE_DEPTH + margin;
        int top = SectGenerator.TERRACE_RISE * SectGenerator.TERRACE_COUNT
                + SectGenerator.CLIFF_BACK_HEIGHT + SectGenerator.TERRACE_RISE + 8;
        int y0 = base.getY() - 64;
        int y1 = base.getY() + top;
        return new BoundingBox(x0, y0, z0, x1, y1, z1);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("bx", base.getX());
        tag.putInt("by", base.getY());
        tag.putInt("bz", base.getZ());
        tag.putLong("seed", siteSeed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box,
                            ChunkPos chunkPos, BlockPos pos) {
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        SectGenerator.SectPlan plan = SectGenerator.plan(siteSeed, base);
        SectMountain mountain = SectGenerator.buildMountain(siteSeed, plan,
                (x, z) -> chunkGenerator.getBaseHeight(base.getX() + x, base.getZ() + z,
                        Heightmap.Types.WORLD_SURFACE_WG, level, randomState));
        SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
        RandomSource templateRandom = RandomSource.create(siteSeed ^ box.minX() * 0x9E3779B9L ^ box.minZ());
        WorldGenSink sink = new WorldGenSink(level, box, base, mountain);
        SectGenerator.writeMountain(sink, plan, mountain, stats);
        SectGenerator.placeCloudSea(sink, plan, mountain, siteSeed, stats);
        SectGenerator.realizeCompound(sink, plan, templateRandom, siteSeed, stats);
    }

    /** Writes to a {@link WorldGenLevel}, clamped to the current chunk region. */
    private static final class WorldGenSink implements SectSink {
        private final WorldGenLevel level;
        private final BoundingBox box;
        private final BlockPos base;
        private final SectMountain mountain;
        private final ServerLevel server;

        WorldGenSink(WorldGenLevel level, BoundingBox box, BlockPos base, SectMountain mountain) {
            this.level = level;
            this.box = box;
            this.base = base;
            this.mountain = mountain;
            this.server = level.getLevel();
        }

        @Override
        public void set(BlockPos at, BlockState state) {
            if (box.isInside(at)) {
                level.setBlock(at, state, Block.UPDATE_CLIENTS);
            }
        }

        @Override
        public int surfaceY(int worldX, int worldZ) {
            return mountain.height(worldX - base.getX(), worldZ - base.getZ());
        }

        @Override
        public Optional<ModBlockFallback.LoadedTemplate> loadTemplate(ResourceLocation id) {
            return ModBlockFallback.loadTemplate(server, id);
        }

        @Override
        public boolean placeTemplate(StructureTemplate template, BlockPos origin, RandomSource random) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setBoundingBox(box);
            return template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
        }
    }
}
