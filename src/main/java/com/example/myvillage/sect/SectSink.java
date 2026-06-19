package com.example.myvillage.sect;

import com.example.myvillage.town.ModBlockFallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Block-writing target for the shared sect realizer, so the same geometry serves
 * the on-the-spot command ({@code ServerLevel}) and worldgen ({@code WorldGenLevel}
 * clamped to a chunk bounding box). The command sink resolves surface height from
 * the live world; the worldgen/force-generate sink resolves it from the derived
 * mountain so terraces and volumes rest on the man-made relief (no float/bury).
 */
interface SectSink {
    void set(BlockPos pos, BlockState state);

    /**
     * Inclusive world-space x/z region the realizer may write to. The realizer
     * tightens its loop bounds to this clip so each call does work proportional
     * only to the clip, not the whole sect footprint. The command sink returns
     * {@link SectGenerator.Clip#UNBOUNDED} (build the whole compound in one
     * pass); the worldgen sink returns the current chunk's column area so a
     * chunk does only its own slice. {@link #set} still filters per cell as a
     * safety net, so cells written one step beyond the clip are dropped here and
     * supplied by the neighbouring chunk's pass.
     */
    default SectGenerator.Clip clip() {
        return SectGenerator.Clip.UNBOUNDED;
    }

    /** Surface Y the realizer should rest on, at absolute world (x, z). */
    int surfaceY(int worldX, int worldZ);

    Optional<ModBlockFallback.LoadedTemplate> loadTemplate(ResourceLocation id);

    boolean placeTemplate(StructureTemplate template, BlockPos origin, RandomSource random);
}
