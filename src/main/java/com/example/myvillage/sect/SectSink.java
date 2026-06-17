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

    /** Surface Y the realizer should rest on, at absolute world (x, z). */
    int surfaceY(int worldX, int worldZ);

    Optional<ModBlockFallback.LoadedTemplate> loadTemplate(ResourceLocation id);

    boolean placeTemplate(StructureTemplate template, BlockPos origin, RandomSource random);
}
