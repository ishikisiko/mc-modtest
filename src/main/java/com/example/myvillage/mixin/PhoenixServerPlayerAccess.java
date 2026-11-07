package com.example.myvillage.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 余烬恢复实体不是一次出生，不能重新获得原版出生保护。 */
@Mixin(value=ServerPlayer.class,remap=false)
public interface PhoenixServerPlayerAccess {
    @Accessor("spawnInvulnerableTime") void myvillage$setEmberSpawnProtection(int ticks);
}
