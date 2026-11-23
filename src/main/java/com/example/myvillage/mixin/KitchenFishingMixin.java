package com.example.myvillage.mixin;

import com.example.myvillage.kitchen.KitchenRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class KitchenFishingMixin {
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow private int nibble;
    @Unique private boolean yuanxian$rollingWait;

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void yuanxian$beforeWait(BlockPos pos, CallbackInfo callback) {
        yuanxian$rollingWait = timeUntilLured <= 0 && timeUntilHooked <= 0 && nibble <= 0;
    }

    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void yuanxian$shortenWait(BlockPos pos, CallbackInfo callback) {
        if (yuanxian$rollingWait && timeUntilLured > 0 && ((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player) {
            int percent = KitchenRuntime.percent(player, "kitchen_cod_cakes");
            if (percent > 0) timeUntilLured = Math.max(1, Math.round(timeUntilLured * (1 - percent / 100.0F)));
        }
    }
}
