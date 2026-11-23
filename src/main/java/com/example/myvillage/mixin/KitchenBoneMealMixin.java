package com.example.myvillage.mixin;

import com.example.myvillage.kitchen.KitchenRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BoneMealItem.class)
public abstract class KitchenBoneMealMixin {
    @Redirect(method = "applyBonemeal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private static void yuanxian$preserve(ItemStack consumed, int amount, ItemStack original, Level level, BlockPos pos, Player player) {
        if (!KitchenRuntime.preserveBoneMeal(player, level.getBlockState(pos))) consumed.shrink(amount);
    }
}
