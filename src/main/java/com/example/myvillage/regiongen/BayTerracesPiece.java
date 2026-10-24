package com.example.myvillage.regiongen;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Writes one chunk column per callback; never acquires a neighbouring chunk or rebuilds FULL chunks. */
public final class BayTerracesPiece extends StructurePiece {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final BlockPos origin;
    private final long siteSeed;
    private final int rotation;

    BayTerracesPiece(BlockPos origin, long siteSeed, int rotation, int minY, int maxY) {
        super(BayTerracesStructures.PIECE.get(), 0, bounds(origin, minY, maxY));
        this.origin = origin;
        this.siteSeed = siteSeed;
        this.rotation = rotation;
    }

    public BayTerracesPiece(CompoundTag tag) {
        super(BayTerracesStructures.PIECE.get(), tag);
        var layout = BayTerracesLayout.current();
        if (!tag.getString("layout_hash").equals(layout.layoutHash))
            throw new IllegalStateException("Bay settlement asset version differs from saved structure; restore matching assets");
        origin = new BlockPos(tag.getInt("ox"), tag.getInt("oy"), tag.getInt("oz"));
        siteSeed = tag.getLong("site_seed");
        rotation = tag.getInt("rotation");
        if (rotation < 0 || rotation > 3 || Math.floorMod(origin.getX(), 16) != 0 || Math.floorMod(origin.getZ(), 16) != 0)
            throw new IllegalArgumentException("Invalid saved bay structure transform");
    }

    private static BoundingBox bounds(BlockPos origin, int minY, int maxY) {
        var layout = BayTerracesLayout.current();
        return new BoundingBox(origin.getX() - layout.margin, minY, origin.getZ() - layout.margin,
                origin.getX() + layout.size + layout.margin - 1, maxY,
                origin.getZ() + layout.size + layout.margin - 1);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("layout_hash", BayTerracesLayout.current().layoutHash);
        tag.putInt("ox", origin.getX()); tag.putInt("oy", origin.getY()); tag.putInt("oz", origin.getZ());
        tag.putLong("site_seed", siteSeed); tag.putInt("rotation", rotation);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        var layout = BayTerracesLayout.current();
        int minX = Math.max(box.minX(), Math.max(chunk.getMinBlockX(), boundingBox.minX()));
        int maxX = Math.min(box.maxX(), Math.min(chunk.getMaxBlockX(), boundingBox.maxX()));
        int minZ = Math.max(box.minZ(), Math.max(chunk.getMinBlockZ(), boundingBox.minZ()));
        int maxZ = Math.min(box.maxZ(), Math.min(chunk.getMaxBlockZ(), boundingBox.maxZ()));
        if (minX > maxX || minZ > maxZ) return;
        var noise = level.getLevel().getChunkSource().randomState();
        Map<String, BlockState> materials = new HashMap<>();
        for (String name : new String[]{"ground", "soil", "rock", "bank", "court", "road", "water"}) {
            String id = layout.material(name);
            materials.put(id, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState());
        }
        BlockState rock = materials.get(layout.material("rock")), soil = materials.get(layout.material("soil"));
        BlockState water = materials.get(layout.material("water")), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            var local = layout.inverse(x - origin.getX(), z - origin.getZ(), rotation);
            if (layout.weight(local.x(), local.z()) == 0) continue;
            // Query unmodified generator terrain, not a neighbour's feature output or this world's edit state.
            int natural = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, noise);
            int surface = layout.surface(local.x(), local.z(), natural, origin.getY());
            int waterY = layout.weight(local.x(), local.z()) == 1
                    ? layout.waterY + origin.getY() : generator.getSeaLevel();
            BlockState top = materials.get(layout.surfaceMaterial(local.x(), local.z(), surface, origin.getY()));
            int bottom = Math.max(level.getMinBuildHeight(), Math.min(natural, surface) - layout.soilDepth);
            int ceiling = Math.min(level.getMaxBuildHeight() - 1,
                    Math.max(natural, origin.getY() + layout.baseY + layout.height));
            for (int y = bottom; y <= ceiling; y++) {
                BlockState state = y < surface - layout.soilDepth ? rock : y < surface - 1 ? soil
                        : y == surface - 1 ? top : y < waterY ? water : air;
                position.set(x, y, z);
                if (box.isInside(position)) level.setBlock(position, state, FLAGS);
            }
        }
        // Rotations map every aligned 16x16 source slice into one aligned world chunk.
        var local = layout.inverse(chunk.getMinBlockX() - origin.getX(), chunk.getMinBlockZ() - origin.getZ(), rotation);
        int tx = Math.floorDiv(local.x(), 16) * 16, tz = Math.floorDiv(local.z(), 16) * 16;
        var tile = layout.tile(tx, tz);
        if (tile == null) return; // The terrain-only skirt has no authored overlay.
        ResourceLocation id = ResourceLocation.parse(tile.template());
        verify(level, id, tile.sha256());
        var template = level.getLevel().getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("Missing bay template " + id));
        if (template.getSize().getX() != 16 || template.getSize().getZ() != 16 || template.getSize().getY() != layout.height)
            throw new IllegalStateException("Invalid bay tile size " + id);
        var rotated = layout.rotate(tx, tz, rotation);
        BlockPos at = new BlockPos(origin.getX() + rotated.x(), origin.getY() + layout.baseY, origin.getZ() + rotated.z());
        Rotation facing = switch (rotation) {
            case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE;
        };
        BoundingBox clip = new BoundingBox(minX, box.minY(), minZ, maxX, box.maxY(), maxZ);
        var settings = new StructurePlaceSettings().setBoundingBox(clip).setRotation(facing)
                .setKnownShape(true).setIgnoreEntities(true);
        if (!template.placeInWorld(level, at, at, settings, RandomSource.create(siteSeed ^ ((long) tx << 32) ^ tz), FLAGS))
            throw new IllegalStateException("Bay template placement failed " + id);
    }

    private static void verify(WorldGenLevel level, ResourceLocation id, String expected) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
        try (var input = level.getLevel().getServer().getResourceManager().getResourceOrThrow(file).open()) {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            if (!actual.equals(expected)) throw new IllegalStateException("Bay template hash differs: " + file);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot verify bay template " + file, e);
        }
    }
}
