package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.PhoenixRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LivingEntity.class,remap=false)
public abstract class PhoenixDeathProtectionMixin {
    @Inject(method="checkTotemDeathProtection",at=@At("HEAD"),cancellable=true)
    private void myvillage$noEmberTotem(DamageSource source,CallbackInfoReturnable<Boolean> ci) {
        if((Object)this instanceof ServerPlayer player&&PhoenixRuntime.active(player))ci.setReturnValue(false);
    }
    @Inject(method="checkTotemDeathProtection",at=@At("RETURN"),cancellable=true)
    private void myvillage$afterTotem(DamageSource source,CallbackInfoReturnable<Boolean> ci) {
        if(!ci.getReturnValue()&&(Object)this instanceof ServerPlayer player&&PhoenixRuntime.tryBegin(player,source))ci.setReturnValue(true);
    }
}
