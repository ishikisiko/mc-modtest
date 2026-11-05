package com.example.myvillage.client.compat;

import com.example.myvillage.compat.waystones.BagAwareItemRequirement;
import net.blay09.mods.waystones.client.requirement.ItemRequirementRenderer;
import net.blay09.mods.waystones.client.requirement.RequirementClientRegistry;
import net.blay09.mods.waystones.client.requirement.RequirementRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/** This class is deliberately not an automatic subscriber: its optional interfaces must stay behind the presence check. */
public final class WaystonesClientCompatibility {
    private WaystonesClientCompatibility() { }

    public static void register() {
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
