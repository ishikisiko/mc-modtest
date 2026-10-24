package com.example.myvillage.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=MinecraftServer.class, remap=false)
public interface RealmServerAccess {
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess myvillage$storage();
}
