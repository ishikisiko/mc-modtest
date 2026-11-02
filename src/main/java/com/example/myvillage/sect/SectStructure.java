package com.example.myvillage.sect;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/** 生物群系与间距约束的自然宗门入口，分块读取当前编译蓝图。 */
public class SectStructure extends Structure {
    public static final MapCodec<SectStructure> CODEC = simpleCodec(SectStructure::new);

    public SectStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        int y=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
        SectBlueprint d=SectBlueprint.current();
        if (Math.max(y,context.heightAccessor().getMinBuildHeight()+1+d.entry.getY())-d.entry.getY()+d.maxY
                >=context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
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
        BlockPos base = SectBlueprint.current().base(SectBlueprint.current().fitAnchor(new BlockPos(cx,y,cz),
                context.heightAccessor().getMinBuildHeight(),context.heightAccessor().getMaxBuildHeight()));
        long siteSeed = context.seed()
                ^ ((((long) base.getX()) << 32) ^ (base.getZ() & 0xffffffffL));
        builder.addPiece(new SectStructurePiece(base, siteSeed));
    }

    @Override
    public StructureType<?> type() {
        return SectStructures.SECT.get();
    }
}
