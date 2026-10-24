package com.example.myvillage.mixin;

import com.example.myvillage.worldplan.runtime.WorldPlanGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent neighbouring decoration from overwriting city cells in either visit order. */
@Mixin(WorldGenRegion.class)
public abstract class WorldPlanReservationMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void worldplan$protect(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> callback){
        WorldGenRegion world=(WorldGenRegion)(Object)this;
        if(!WorldPlanGuard.allows(world,pos)||!com.example.myvillage.regiongen.BayTerracesGuard.allows(world,pos))callback.setReturnValue(false);
    }
}
