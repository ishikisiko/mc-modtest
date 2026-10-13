package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.combat.ClientSwordQi;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Read-only display of the synchronized profile and realm-stage cap. */
public final class QiBarLayer {
    private static long previous = Long.MIN_VALUE;
    private static long changedAt = Long.MIN_VALUE;

    private QiBarLayer() { }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "spiritual_power"), QiBarLayer::render);
    }

    public static void clear() {
        previous = Long.MIN_VALUE;
        changedAt = Long.MIN_VALUE;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()) return;
        var profile = ClientCultivationState.latest().orElse(null);
        if (profile == null) return;
        long maximum = mc.level.registryAccess().registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .flatMap(realm -> realm.stages().stream().filter(stage -> stage.id().equals(profile.stageId())).findFirst())
                .map(stage -> stage.spiritualPowerCap()).orElse(0L);
        if (maximum <= 0) return;
        long now = ClientSwordQi.presentationTick();
        if (previous != profile.currentSpiritualPower()) {
            previous = profile.currentSpiritualPower();
            changedAt = now;
        }
        if (!ClientSwordQi.cultivationMode() && now - changedAt > 60) return;
        var bar = QiBarLayout.at(graphics.guiWidth(), graphics.guiHeight());
        int color = ClientSwordQi.recentlyRefused() ? 0xFFFF7979 : 0xFF8CE5E7;
        graphics.fill(bar.x() - 1, bar.y() - 1, bar.x() + bar.width() + 1, bar.y() + 5, 0xB0101822);
        graphics.fill(bar.x(), bar.y(), bar.x() + bar.fill(previous, maximum), bar.y() + 4, color);
        graphics.drawCenteredString(mc.font, Component.translatable("hud.myvillage.qi", previous, maximum),
                graphics.guiWidth() / 2, bar.y() - 11, color);
        double charge = ClientSwordQi.localCharge();
        if (charge >= 0) {
            graphics.fill(bar.x(), bar.y() + 7, bar.x() + (int) Math.round(bar.width() * charge), bar.y() + 9, 0xFFF2E4AE);
            graphics.drawCenteredString(mc.font, Component.translatable("hud.myvillage.qi.charging"),
                    graphics.guiWidth() / 2, bar.y() - 22, 0xFFF2E4AE);
        }
    }
}
