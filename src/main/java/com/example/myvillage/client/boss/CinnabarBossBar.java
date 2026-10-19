package com.example.myvillage.client.boss;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.boss.BossBarPresentation;
import com.example.myvillage.client.cultivation.QiBarLayer;
import com.example.myvillage.client.cultivation.QiBarLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/** A: 朱砂横锋. Only explicitly structured mod boss events replace the native bar. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class CinnabarBossBar {
    private static final Style TITLE = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "boss_title"));
    private static final Style DETAIL = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "boss_detail"));
    private static final Map<UUID, BossBarTrail> TRAILS = new HashMap<>();
    private static ClientLevel world;
    private CinnabarBossBar() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) { TRAILS.clear(); world = current; }
        long now = Util.getMillis();
        TRAILS.values().removeIf(trail -> now - trail.lastSeen > 2000);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear(); world = null;
    }
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        var data = BossBarPresentation.read(event.getBossEvent().getName());
        if (data.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        var qi = QiBarLayout.at(g.guiWidth(), g.guiHeight());
        var layout = BossBarLayout.at(g.guiWidth(), event.getY(), QiBarLayer.isVisible(),
                qi.frameX() + qi.frameWidth(), qi.frameY() + qi.frameHeight());
        event.setCanceled(true);
        event.setIncrement(layout.increment());
        float health = BossBarLayout.fraction(event.getBossEvent().getProgress());
        long now = Util.getMillis();
        UUID id = event.getBossEvent().getId();
        if (TRAILS.size() >= 128 && !TRAILS.containsKey(id)) TRAILS.clear();
        float trail = TRAILS.computeIfAbsent(id, ignored -> new BossBarTrail(health, now)).sample(health, now);
        paint(g, mc.font, layout, data.get(), health, trail);
    }

    private static void paint(GuiGraphics g, Font font, BossBarLayout b, BossBarPresentation data, float hp, float trail) {
        int x = b.x(), y = b.barY(), w = b.width();
        // Thin, irregular dry-ink silhouette. No changing noise or particles during combat.
        for (int column = -10; column < w + 11; column++) {
            int tip = column < 0 ? -column : Math.max(0, column - w);
            int fringe = Math.floorMod(column * 17, 7) == 0 ? 1 : 0;
            int inset = tip == 0 ? 0 : Math.min(4, tip / 2);
            g.fill(x + column, y - 2 + inset - fringe, x + column + 1, y + 7 - inset, 0xE51B2523);
        }
        g.fill(x - 2, y - 2, x + w - 1, y - 1, 0xB5D0BE91);
        g.fill(x + 1, y + 7, x + w + 3, y + 8, 0xA0C1AE85);
        g.fill(x, y, x + w, y + 5, 0xFF433D35);
        g.fill(x, y, x + b.fill(trail), y + 5, 0xFFC2A16C);
        int filled = b.fill(hp);
        for (int column = 0; column < filled; column++) {
            float t = column / (float) Math.max(1, w - 1);
            int color = 0xFF000000 | Math.round(111 + 95 * t) << 16 | Math.round(40 + 76 * t) << 8 | Math.round(36 + 48 * t);
            g.fill(x + column, y, x + column + 1, y + 5, color);
        }
        if (filled > 0) g.fill(x, y, x + filled, y + 1, 0x70E6AB82);
        g.fill(x - 10, y + 3, x - 3, y + 4, 0xA0D0BE91);
        g.fill(x + w + 3, y + 1, x + w + 10, y + 2, 0xA0D0BE91);

        Component title = fit(font, data.title().getString(), w - 36, TITLE);
        int titleWidth = font.width(title), titleX = x + (w - titleWidth) / 2;
        drawText(g, font, title, titleX, b.top() + 8, 0xFFF1E7D0);
        int sealX = titleX + titleWidth + 5;
        g.fill(sealX, b.top() + 3, sealX + 14, b.top() + 17, data.enraged() ? 0xFFA43C2D : 0xFF823F32);
        g.renderOutline(sealX, b.top() + 3, 14, 14, 0xBFD6AC86);
        Component seal = Component.translatable("hud.myvillage.boss.seal").withStyle(DETAIL);
        g.drawString(font, seal, sealX + (14 - font.width(seal)) / 2, b.top() + 6, 0xFFF3DFBB, false);

        Component percent = Component.literal(Math.round(hp * 100) + "%").withStyle(DETAIL);
        Component realm = fit(font, data.realm().getString(), w / 4, DETAIL);
        int left = x + font.width(realm) + 8, right = x + w - font.width(percent) - 8;
        Component status = fit(font, data.phase().getString() + " · " + data.horn().getString(), Math.max(1, right - left), DETAIL);
        int statusX = Math.clamp(x + (w - font.width(status)) / 2, left, Math.max(left, right - font.width(status)));
        drawText(g, font, realm, x, b.detailsY(), 0xFFE1D8C1);
        drawText(g, font, status, statusX, b.detailsY(), data.enraged() ? 0xFFEAA58E : 0xFFE1D8C1);
        drawText(g, font, percent, x + w - font.width(percent), b.detailsY(), 0xFFF1E7D0);
    }
    private static Component fit(Font font, String value, int width, Style style) {
        Component text = Component.literal(value).withStyle(style);
        if (font.width(text) <= width) return text;
        int suffix = font.width(Component.literal("…").withStyle(style));
        return Component.literal(font.getSplitter().plainHeadByWidth(value, Math.max(0, width - suffix), style) + "…").withStyle(style);
    }
    private static void drawText(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        // A restrained fractional offset preserves contrast without the vanilla hard pixel shadow.
        g.pose().pushPose();
        g.pose().translate(0.35F, 0.5F, 0);
        g.drawString(font, text, x, y, 0x900C1411, false);
        g.pose().popPose();
        g.drawString(font, text, x, y, color, false);
    }
}
