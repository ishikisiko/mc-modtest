package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.HeavyHandRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class FateWallCollisionMixin {
    @Unique private AABB yuanxian$wallStart;
    @Unique private Vec3 yuanxian$wallPosition;

    @Inject(method = "move", at = @At("HEAD"))
    private void yuanxian$trackWallMovement(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (type != MoverType.SELF || !HeavyHandRuntime.tracking(entity)) return;
        yuanxian$wallStart = entity.getBoundingBox();
        yuanxian$wallPosition = entity.position();
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void yuanxian$detectWallMovement(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (yuanxian$wallStart == null) return;
        Entity entity = (Entity) (Object) this;
        var bounds = yuanxian$wallStart;
        var origin = yuanxian$wallPosition;
        yuanxian$wallStart = null;
        yuanxian$wallPosition = null;
        HeavyHandRuntime.collide(entity, bounds, movement, entity.position().subtract(origin));
    }
}
