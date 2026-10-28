package com.example.myvillage.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface MeditationCameraAccess {
    @Invoker("setPosition") void myvillage$meditationPosition(Vec3 position);
}
