package com.example.myvillage.mixin;

import com.example.myvillage.kitchen.KitchenRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class KitchenFoodDataMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yuanxian$fasting(Player player, CallbackInfo callback) {
        if (!(player instanceof ServerPlayer server) || !KitchenRuntime.fasting(server)) return;
        FoodData food = (FoodData) (Object) this;
        food.setFoodLevel(20);
        food.setSaturation(0);
        food.setExhaustion(0);
        callback.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void yuanxian$scaleNaturalHealing(Player player, float amount) {
        KitchenRuntime.vanillaHeal(player, amount);
    }
}
