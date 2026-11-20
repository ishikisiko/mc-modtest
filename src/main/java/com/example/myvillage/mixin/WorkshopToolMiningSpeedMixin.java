package com.example.myvillage.mixin;

import com.example.myvillage.forging.WorkshopTool;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemStack.class, remap = false)
public abstract class WorkshopToolMiningSpeedMixin {
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float yuanxian$applyToolQuality(float speed, BlockState state) {
        return WorkshopTool.qualityMiningSpeed((ItemStack) (Object) this, state, speed);
    }
}
