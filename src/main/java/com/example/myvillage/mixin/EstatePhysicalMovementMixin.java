package com.example.myvillage.mixin;

import com.example.myvillage.estate.EstateRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes pushes and server-authored movement that do not arrive as movement packets. */
@Mixin(value=Entity.class,remap=false)
public abstract class EstatePhysicalMovementMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void myvillage$estatePhysics(MoverType type,Vec3 delta,CallbackInfo ci) {
        if((Object)this instanceof ServerPlayer player
                && EstateRuntime.movement(player,player.getX()+delta.x,player.getZ()+delta.z))ci.cancel();
    }
}
