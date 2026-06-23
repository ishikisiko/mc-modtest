package com.example.myvillage.sect;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * Custom sect {@link Structure}: a rare, biome-gated, world-seed-reproducible
 * landmark sited during chunk generation. Siting + spacing + biome gating come
 * from the datapack structure/structure_set/biome-tag; this class only resolves
 * the generation point and emits the single {@link SectStructurePiece} that
 * derives the mountain and bakes the compound into the chunks (no force-load).
 */
public class SectStructure extends Structure {
    public static final MapCodec<SectStructure> CODEC = simpleCodec(SectStructure::new);

    public SectStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // onTopOfChunkCenter enforces the biome predicate + a surface anchor;
        // the piece re-derives the exact base so siting is seed-reproducible.
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> generatePieces(builder, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        int y = context.chunkGenerator().getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        BlockPos base = new BlockPos(cx - SectGenerator.SITE_WIDTH / 2, y,
                cz - SectGenerator.SITE_DEPTH / 2);
        long siteSeed = context.seed()
                ^ ((((long) base.getX()) << 32) ^ (base.getZ() & 0xffffffffL));
        builder.addPiece(new SectStructurePiece(base, siteSeed));
    }

    @Override
    public StructureType<?> type() {
        return SectStructures.SECT;
    }
}
