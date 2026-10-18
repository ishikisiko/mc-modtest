package com.example.myvillage.mixin;

import com.example.myvillage.exploration.boss.BossRuntime;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Only the native charge heal invocation; passive regeneration and outside heals are untouched. */
@Mixin(value = WitherBoss.class, remap = false)
public abstract class WitherChargeHealingMixin {
    @ModifyArg(
            method = "customServerAiStep()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;heal(F)V",
                    ordinal = 0, remap = false),
            index = 0, require = 1, allow = 1, remap = false)
    private float myvillage$preserveChargeHealingFraction(float amount) {
        // The final charge step has already changed Invul to zero before reaching this call.
        // Its exact bytecode call site, rather than Invul/amount guesses, identifies the source.
        return BossRuntime.witherChargeHealing((WitherBoss) (Object) this, amount);
    }
}
