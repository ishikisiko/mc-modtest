package com.example.myvillage.mixin;

import com.example.myvillage.wilderness.runtime.VanillaMonsterRealms;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native reproduction does not finalize a spawn or publish the slime split/conversion events. */
@Mixin(value = Shulker.class, remap = false)
public abstract class ShulkerReproductionMixin {
    @ModifyArg(
            method = "hitByShulkerBullet()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                    ordinal = 0, remap = false),
            index = 0, require = 1, allow = 1, remap = false)
    private Entity myvillage$inheritReproducedBirth(Entity child) {
        VanillaMonsterRealms.inheritShulkerReproduction((Shulker) (Object) this, child);
        return child;
    }
}
