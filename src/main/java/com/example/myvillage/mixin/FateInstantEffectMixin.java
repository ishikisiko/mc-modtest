package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.FateDefenses;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
abstract class FateInstantEffectMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void yuanxian$wardInstantHarm(Entity source, Entity owner, LivingEntity target,
            int amplifier, double intensity, CallbackInfo ci) {
        if (((MobEffect) (Object) this).getCategory() == MobEffectCategory.HARMFUL
                && FateDefenses.immune(target)) ci.cancel();
    }
}
