package com.example.myvillage.mixin;

import com.example.myvillage.exploration.boss.BossRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The permanent boss modifier also identifies the attribute on the client and after an NBT load. */
@Mixin(AttributeInstance.class)
public abstract class ExplorationBossHealthMixin {
    @WrapOperation(method = "calculateValue", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/Attribute;sanitizeValue(D)D"), require = 1, allow = 1)
    private double yuanxian$bossHealthLimit(Attribute attribute, double value, Operation<Double> original) {
        var instance = (AttributeInstance) (Object) this;
        if (instance.getAttribute().equals(Attributes.MAX_HEALTH) && instance.hasModifier(BossRuntime.HEALTH)
                && attribute instanceof RangedAttribute ranged && value > ranged.getMaxValue() && value <= Float.MAX_VALUE) {
            return value;
        }
        return original.call(attribute, value);
    }
}
