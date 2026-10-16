package com.example.myvillage.client.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.forging.ForgingQualityDisplay;
import com.example.myvillage.item.CombatWeaponItem;
import com.example.myvillage.item.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** One presentation palette across inventory, containers, hotbar and item tooltips. */
public final class ForgingQualityFrames {
    private ForgingQualityFrames() {}

    private static int color(ItemStack stack) {
        return 0xFF000000 | ForgingQualityDisplay.color(ForgingQualityDisplay.quality(stack));
    }

    private static boolean decorate(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int color = color(stack);
        graphics.pose().pushPose();
        // The 1px frame sits outside the 16px icon and below vanilla count text (z=200).
        graphics.pose().translate(0, 0, 150);
        graphics.fill(x - 1, y - 1, x + 17, y, color);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, color);
        graphics.fill(x - 1, y, x, y + 16, color);
        graphics.fill(x + 16, y, x + 17, y + 16, color);
        graphics.pose().popPose();
        return true;
    }

    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterItemDecorationsEvent event) {
            event.register(ModItems.QINGFENG_SWORD.get(), ForgingQualityFrames::decorate);
            event.register(ModItems.LINGXIAO_SPEAR.get(), ForgingQualityFrames::decorate);
        }
    }

    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Tooltips {
        @SubscribeEvent
        public static void color(RenderTooltipEvent.Color event) {
            if (!(event.getItemStack().getItem() instanceof CombatWeaponItem)) return;
            int color = ForgingQualityFrames.color(event.getItemStack());
            event.setBorderStart(color);
            event.setBorderEnd(color);
        }
    }
}
