package com.example.myvillage.mixin;

import com.example.myvillage.regiongen.BayTerracesGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class BayTerracesDecorationMixin {
    @WrapMethod(method="applyBiomeDecoration")
    private void bayTerraces$decorate(WorldGenLevel world, ChunkAccess chunk, StructureManager structures, Operation<Void> original) {
        try (var scope = BayTerracesGuard.decorating(world, chunk)) {
            original.call(world, chunk, structures);
        }
    }
}
