package com.example.myvillage.mixin;

import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = CombatRules.class, remap = false)
public abstract class CombatRulesMixin {
    @ModifyArgs(
            method = "getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 0, remap = false),
            require = 1, allow = 1, remap = false)
    private static void myvillage$normalizeArmorPressure(
            Args arguments, LivingEntity defender, float damage, DamageSource source, float armor, float toughness) {
        float original = arguments.get(0);
        arguments.set(0, RealmArmorRuntime.normalizeUnclamped(
                defender, source, damage, armor, original, arguments.get(1), arguments.get(2)));
    }
}
