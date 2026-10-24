package com.example.myvillage.mixin;

import com.example.myvillage.worldplan.runtime.WorldPlanGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Function;

/** OreFeature writes sections directly, so WorldGenRegion.setBlock alone cannot protect a city. */
@Mixin(OreFeature.class)
public abstract class WorldPlanOreMixin {
    @Inject(method="canPlaceOre",at=@At("HEAD"),cancellable=true)
    private static void worldplan$reserve(BlockState state,Function<BlockPos,BlockState> states,
            RandomSource random,OreConfiguration configuration,OreConfiguration.TargetBlockState target,
            BlockPos.MutableBlockPos pos,CallbackInfoReturnable<Boolean> callback){
        if(!WorldPlanGuard.allowsOre(pos)||!com.example.myvillage.regiongen.BayTerracesGuard.allowsOre(pos))callback.setReturnValue(false);
    }
}
