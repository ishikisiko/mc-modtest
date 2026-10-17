package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.combat.ClientSwordQi;
import com.example.myvillage.cultivation.qi.SpiritualPower;
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
        var snapshot = ClientCultivationState.snapshot().orElse(null);
        if (snapshot == null) return;
        var profile = snapshot.profile();
        long maximum = SpiritualPower.cap(mc.level.registryAccess(), profile, snapshot.fates());
        if (maximum <= 0) return;
        long now = ClientSwordQi.presentationTick();
        if (previous != profile.currentSpiritualPower()) {
            previous = profile.currentSpiritualPower();
            changedAt = now;
        }
        if (!ClientSwordQi.cultivationMode() && now - changedAt > 60) return;
        var bar = QiBarLayout.at(graphics.guiWidth(), graphics.guiHeight());
        boolean refused = ClientSwordQi.recentlyRefused();
        frame(graphics, bar, refused);
        seal(graphics, bar, mc);
        int textColor = refused ? 0xFFFFA696 : 0xFFE5F0DE;
        graphics.drawString(mc.font, Component.translatable("hud.myvillage.qi.label"),
                bar.x(), bar.textY(), 0xFFCDDBC8, false);
        String balance = previous + " / " + maximum;
        graphics.drawString(mc.font, balance, bar.x() + bar.width() - mc.font.width(balance),
                bar.textY(), textColor, false);
        graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + 8, 0xFF0B1818);
        graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + 1, 0xFF304941);
        int fill = bar.fill(previous, maximum);
        for (int column = 0; column < fill; column++) {
            float t = column / (float) Math.max(1, bar.width() - 1);
            int r = Math.round(60 + 112 * t), g = Math.round(155 + 77 * t), b = Math.round(144 + 73 * t);
            int color = refused ? 0xFFE78D7C : (0xFF000000 | r << 16 | g << 8 | b);
            graphics.fill(bar.x() + column, bar.y() + 2, bar.x() + column + 1, bar.y() + 6, color);
        }
        double charge = ClientSwordQi.localCharge();
        if (charge >= 0) {
            graphics.drawString(mc.font, Component.translatable("hud.myvillage.qi.charging"),
                    bar.x(), bar.chargeY(), 0xFFDEC88C, false);
            graphics.fill(bar.chargeX(), bar.chargeY() + 3,
                    bar.chargeX() + bar.chargeWidth(), bar.chargeY() + 5, 0xFF24382F);
            graphics.fill(bar.chargeX(), bar.chargeY() + 3,
                    bar.chargeX() + bar.chargeFill(charge), bar.chargeY() + 5, 0xFFD8BD72);
        }
    }

    private static void frame(GuiGraphics graphics, QiBarLayout bar, boolean refused) {
        int x = bar.frameX(), y = bar.frameY(), w = bar.frameWidth(), h = bar.frameHeight();
        graphics.fill(x + 2, y + 2, x + w + 1, y + h + 1, 0x55070F0E);
        graphics.fill(x, y, x + w, y + h, refused ? 0xDA57423A : 0xDA647E65);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xEC0A1917);
        for (int dx : new int[]{0, w - 4}) {
            for (int dy : new int[]{0, h - 3}) {
                graphics.fill(x + dx, y + dy, x + dx + 4, y + dy + 3, 0xFFBAA06A);
            }
        }
    }

    private static void seal(GuiGraphics graphics, QiBarLayout bar, Minecraft mc) {
        int cx = bar.frameX() + 14, cy = bar.frameY() + 18;
        for (int row = -13; row <= 13; row++) {
            int radius = Math.max(0, 11 - Math.abs(row) * 11 / 13);
            graphics.fill(cx - radius, cy + row, cx + radius + 1, cy + row + 1, 0xFF9BC8A6);
            if (radius > 1) graphics.fill(cx - radius + 1, cy + row, cx + radius, cy + row + 1,
                    row < 0 ? 0xFF2E6959 : 0xFF234F45);
        }
        var glyph = Component.translatable("hud.myvillage.qi.seal");
        graphics.drawString(mc.font, glyph, cx - mc.font.width(glyph) / 2, cy - 4, 0xFFD6E7C8, false);
    }
}
