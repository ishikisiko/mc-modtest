package com.example.myvillage.mixin;

import com.example.myvillage.story.realm.QianzhenRealms;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

/** Reject before ticket acquisition, dimension packets or entity insertion, including command teleports. */
@Mixin(value=ServerPlayer.class,remap=false)
public abstract class RealmPlayerAccessMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void myvillage$checkedTeleport(ServerLevel level,double x,double y,double z,Set<RelativeMovement> moves,float yaw,float pitch,CallbackInfoReturnable<Boolean> ci){
        if(!QianzhenRealms.allowed((ServerPlayer)(Object)this,level))ci.setReturnValue(false);
    }
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"),cancellable=true)
    private void myvillage$checkedTransfer(ServerLevel level,double x,double y,double z,float yaw,float pitch,CallbackInfo ci){
        if(!QianzhenRealms.allowed((ServerPlayer)(Object)this,level))ci.cancel();
    }
    @Inject(method="changeDimension",at=@At("HEAD"),cancellable=true)
    private void myvillage$checkedDimension(DimensionTransition transition,CallbackInfoReturnable<Entity> ci){
        if(!QianzhenRealms.allowed((ServerPlayer)(Object)this,transition.newLevel()))ci.setReturnValue(null);
    }
    @Inject(method="setCamera",at=@At("HEAD"),cancellable=true)
    private void myvillage$checkedSpectator(Entity target,CallbackInfo ci){
        if(target!=null&&target.level() instanceof ServerLevel level&&!QianzhenRealms.allowed((ServerPlayer)(Object)this,level))ci.cancel();
    }
    @ModifyReturnValue(method="findRespawnPositionAndUseSpawnBlock",at=@At("RETURN"))
    private DimensionTransition myvillage$privateRespawn(DimensionTransition original){
        return QianzhenRealms.respawn((ServerPlayer)(Object)this,original);
    }
    @Inject(method="findRespawnPositionAndUseSpawnBlock",at=@At("HEAD"),cancellable=true)
    private void myvillage$privateDeath(boolean keepInventory,DimensionTransition.PostDimensionTransition post,CallbackInfoReturnable<DimensionTransition> ci){
        var p=(ServerPlayer)(Object)this;
        if(QianzhenRealms.privateLevel(p.serverLevel())&&QianzhenRealms.allowed(p,p.serverLevel()))
            ci.setReturnValue(QianzhenRealms.personalRespawn(p,post));
    }
}
