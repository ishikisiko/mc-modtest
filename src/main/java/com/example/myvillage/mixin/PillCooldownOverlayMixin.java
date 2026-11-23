package com.example.myvillage.mixin;

import com.example.myvillage.client.item.PillCooldownOverlay;
import com.example.myvillage.item.AlchemyPillItem;
import com.example.myvillage.item.FoundationPillItem;
import com.example.myvillage.item.NascentPillItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class PillCooldownOverlayMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"))
    private void yuanxian$drawPillCooldown(Font font, ItemStack stack, int x, int y, String label, CallbackInfo ci) {
        if (!yuanxian$isPill(stack.getItem())) return;
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        float remaining = client.player.getCooldowns().getCooldownPercent(stack.getItem(),
                client.getTimer().getGameTimeDeltaPartialTick(true));
        if (remaining > 0) PillCooldownOverlay.render((GuiGraphics) (Object) this, x, y, remaining);
    }

    @WrapOperation(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/Item;F)F"))
    private float yuanxian$skipVanillaPillCooldown(ItemCooldowns cooldowns, Item item, float partialTick,
            Operation<Float> original) {
        return yuanxian$isPill(item) ? 0 : original.call(cooldowns, item, partialTick);
    }

    private static boolean yuanxian$isPill(Item item) {
        return item instanceof AlchemyPillItem || item instanceof FoundationPillItem || item instanceof NascentPillItem;
    }
}
