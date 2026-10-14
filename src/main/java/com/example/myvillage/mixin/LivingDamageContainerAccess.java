package com.example.myvillage.mixin;

import java.util.Stack;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NeoForge exposes a container on Pre, but not at the armor call or on Post. */
@Mixin(value = LivingEntity.class, remap = false)
public interface LivingDamageContainerAccess {
    @Accessor(value = "damageContainers", remap = false)
    Stack<DamageContainer> myvillage$damageContainers();
}
