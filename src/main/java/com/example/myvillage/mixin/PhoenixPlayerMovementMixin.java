package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.PhoenixRuntime;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ServerPlayer.class,remap=false)
public abstract class PhoenixPlayerMovementMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void myvillage$noEmberTeleport(ServerLevel level,double x,double y,double z,Set<RelativeMovement> moves,float yaw,float pitch,CallbackInfoReturnable<Boolean> ci) {
        if(PhoenixRuntime.active((ServerPlayer)(Object)this))ci.setReturnValue(false);
    }
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"),cancellable=true)
    private void myvillage$noEmberTransfer(ServerLevel level,double x,double y,double z,float yaw,float pitch,CallbackInfo ci) {
        if(PhoenixRuntime.active((ServerPlayer)(Object)this))ci.cancel();
    }
    @Inject(method="changeDimension",at=@At("HEAD"),cancellable=true)
    private void myvillage$noEmberDimension(DimensionTransition transition,CallbackInfoReturnable<Entity> ci) {
        if(PhoenixRuntime.active((ServerPlayer)(Object)this))ci.setReturnValue(null);
    }
}
