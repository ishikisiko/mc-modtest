package com.example.myvillage.mixin;

import com.example.myvillage.kitchen.KitchenRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class KitchenPlayerFoodMixin {
    @Redirect(method = "eat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void yuanxian$applyFood(FoodData data, FoodProperties properties) {
        if (!((Object) this instanceof ServerPlayer server) || !KitchenRuntime.fasting(server)) data.eat(properties);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void yuanxian$peacefulHealing(Player player, float amount) {
        KitchenRuntime.vanillaHeal(player, amount);
    }
}
