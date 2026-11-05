package com.example.myvillage.client.compat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.compat.waystones.BagAwareItemRequirement;
import net.blay09.mods.waystones.client.requirement.ItemRequirementRenderer;
import net.blay09.mods.waystones.client.requirement.RequirementClientRegistry;
import net.blay09.mods.waystones.client.requirement.RequirementRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaystonesClientBootstrap {
    private WaystonesClientBootstrap() { }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("waystones")) event.enqueueWork(WaystonesClientBootstrap::register);
    }

    private static void register() {
        RequirementClientRegistry.registerRenderer(BagAwareItemRequirement.class, new RequirementRenderer<BagAwareItemRequirement>() {
            private final ItemRequirementRenderer nativeRenderer = new ItemRequirementRenderer();

            @Override
            public void renderWidget(Player player, BagAwareItemRequirement requirement, GuiGraphics graphics,
                                     int x, int y, float partialTicks, int mouseX, int mouseY) {
                nativeRenderer.renderWidget(player, requirement, graphics, x, y, partialTicks, mouseX, mouseY);
            }

            @Override public int getOrder() { return nativeRenderer.getOrder(); }
        });
    }
}
