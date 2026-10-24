package com.example.myvillage.regiongen;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A biome-gated native structure: ordinary world seeds choose its site and rotation. */
public final class BayTerracesStructure extends Structure {
    public static final MapCodec<BayTerracesStructure> CODEC = simpleCodec(BayTerracesStructure::new);
    public BayTerracesStructure(StructureSettings settings) { super(settings); }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // V5 places the same authored layout at frozen plan sites, like its main city.
        if(context.chunkGenerator() instanceof com.example.myvillage.worldplan.runtime.PlannedChunkGenerator planned
                &&planned.rules().settlements!=null)return Optional.empty();
        var layout = BayTerracesLayout.current();
        int cx = context.chunkPos().getMinBlockX(), cz = context.chunkPos().getMinBlockZ();
        int[] offsets = layout.sampleOffsets(), samples = new int[offsets.length * offsets.length];
        int index = 0;
        for (int dx : offsets) for (int dz : offsets) {
            int y = context.chunkGenerator().getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            if (y < context.chunkGenerator().getSeaLevel() + layout.minAboveSea || y > layout.maxSurface)
                return Optional.empty();
            samples[index++] = y;
        }
        Arrays.sort(samples);
        if (samples[samples.length - 1] - samples[0] > layout.maxRelief) return Optional.empty();
        int surface = samples[samples.length / 2];
        int offsetY = surface - layout.anchorLevel;
        if (offsetY + layout.baseY < context.heightAccessor().getMinBuildHeight()
                || offsetY + layout.baseY + layout.height >= context.heightAccessor().getMaxBuildHeight())
            return Optional.empty();
        BlockPos origin = new BlockPos(cx - layout.size / 2, offsetY, cz - layout.size / 2);
        long siteSeed = layout.siteSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        int rotation = (int) (siteSeed & 3L);
        return Optional.of(new GenerationStub(new BlockPos(cx, surface, cz), builder ->
                builder.addPiece(new BayTerracesPiece(origin, siteSeed, rotation,
                        context.heightAccessor().getMinBuildHeight(), context.heightAccessor().getMaxBuildHeight() - 1))));
    }

    @Override public StructureType<?> type() { return BayTerracesStructures.TYPE.get(); }
}
