package com.example.myvillage.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 续接剩余归属窗口，不因重启重新赠送击杀归属时间。 */
@Mixin(value=LivingEntity.class,remap=false)
public interface PhoenixLivingCreditAccess {
    @Accessor("lastHurtByPlayerTime") int myvillage$emberCreditTicks();
    @Accessor("lastHurtByPlayerTime") void myvillage$emberCreditTicks(int ticks);
}
