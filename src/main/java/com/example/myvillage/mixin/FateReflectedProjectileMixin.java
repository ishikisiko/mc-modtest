package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.FateDefenses;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class FateReflectedProjectileMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void yuanxian$skipReflector(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Projectile shot = (Projectile) (Object) this;
        if (FateDefenses.reflected(shot) && shot.getOwner() == target) cir.setReturnValue(false);
    }
}
