package com.example.myvillage.client.arrival;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.arrival.ArrivalPayload;
import com.example.myvillage.arrival.ArrivalRules;
import com.example.myvillage.arrival.ArrivalSequence;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** A: centered province calligraphy. C: vertical town calligraphy on a paper slip. */
@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ArrivalClient {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final ArrivalSequence SEQUENCE = new ArrivalSequence(ArrivalRules.INSTANCE);
    private static ClientLevel level;
    private static LocalPlayer player;
    private static ArrivalSequence.Card previous;

    public ArrivalClient(IEventBus modBus) {
        ArrivalPayload.installReceiver(ArrivalClient::receive);
        modBus.addListener(ArrivalClient::layers);
        NeoForge.EVENT_BUS.addListener(ArrivalClient::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "arrival"), ArrivalClient::render);
    }

    private static void clear() { SEQUENCE.clear(); level = null; player = null; previous = null; }

    private static boolean bindWorld() {
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.player != player) { clear(); level = mc.level; player = mc.player; }
        return level != null && player != null;
    }

    private static void receive(ArrivalPayload notice) {
        if (!bindWorld() || !notice.dimension().equals(level.dimension().location())) return;
        SEQUENCE.accept(notice.location(), notice.showRegion(), notice.showTown());
    }

    private static boolean visible() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && !mc.options.hideGui && mc.screen == null;
    }

    private static void tick(ClientTickEvent.Post e) {
        if (!bindWorld()) return;
        if (!player.isAlive() || player.isSpectator()) { SEQUENCE.clear(); previous = null; return; }
        if (!visible() || Minecraft.getInstance().isPaused()) return;
        SEQUENCE.tick();
        var current = SEQUENCE.current().orElse(null);
        if (current != null && !current.equals(previous))
            LOG.info("ARRIVAL_UI kind={} name={} region={} town={}", current.kind(), current.name(),
                    current.location().regionId(), current.location().townId());
        previous = current;
    }

    /** The town slip temporarily reserves the right side used by the quest tracker. */
    public static boolean occupiesRightSide() {
        return visible() && SEQUENCE.current().filter(card -> card.kind() == ArrivalSequence.Kind.TOWN).isPresent();
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!visible() || level != Minecraft.getInstance().level || player != Minecraft.getInstance().player) return;
        var card = SEQUENCE.current().orElse(null);
        if (card == null) return;
        float opacity = SEQUENCE.opacity(delta.getGameTimeDeltaPartialTick(false));
        // Font treats alpha below 4 as unspecified/opaque; skip those nearly invisible frames.
        if (opacity < .03f) return;
        float unit = Math.min(g.guiWidth() / 1920f, g.guiHeight() / 1080f);
        float w = g.guiWidth() / unit, h = g.guiHeight() / unit;
        g.pose().pushPose();
        g.pose().scale(unit, unit, 1);
        if (card.kind() == ArrivalSequence.Kind.REGION) province(g, card, w, h, opacity);
        else town(g, card, w, h, opacity);
        g.pose().popPose();
    }

    private static void province(GuiGraphics g, ArrivalSequence.Card card, float w, float h, float alpha) {
        // A faint local scrim keeps pale letters legible without dimming the whole game.
        for (int i = 0; i < 24; i++) {
            float t = i / 24f;
            fill(g, w * (.18f + .012f * t), h * (.26f + .10f * t),
                    w * (.82f - .012f * t), h * (.70f - .10f * t), color(0x07120f, alpha * .008f));
        }
        centered(g, CultivationInkTheme.book("洲域"), w / 2, h * .31f, 2.2f, w * .5f, 0xded4b6, alpha, true);
        float rise = (1 - Math.min(1, SEQUENCE.age() / (float)ArrivalRules.INSTANCE.fadeInTicks())) * 22;
        centered(g, CultivationInkTheme.brush(card.name()), w / 2, h * .405f + rise,
                h * .19f / 14, w * .64f, 0xf3ead3, alpha, true);
        float lineY = h * .64f;
        float spread = Math.min(1, (SEQUENCE.age() + 1f) / ArrivalRules.INSTANCE.fadeInTicks());
        for (int i = 0; i < 32; i++) {
            float strength = 1 - Math.abs((i + .5f) / 16 - 1);
            float x = w / 2 + (i / 32f - .5f) * w * .40f * spread;
            fill(g, x, lineY, x + w * .40f * spread / 32, lineY + 2, color(0xd6c69c, alpha * strength));
        }
        int cx = Math.round(w / 2), cy = Math.round(lineY + 34);
        for (int y = -5; y <= 5; y++) {
            int radius = 5 - Math.abs(y);
            fill(g, cx - radius, cy + y, cx + radius + 1, cy + y + 1, color(0xd3bb84, alpha));
        }
    }

    private static void town(GuiGraphics g, ArrivalSequence.Card card, float w, float h, float alpha) {
        float pw = Math.min(230, w * .12f), ph = h * .55f;
        float x = w - w * .07f - pw, y = h * .25f;
        float reveal = Math.min(1, (SEQUENCE.age() + 1f) / ArrivalRules.INSTANCE.fadeInTicks());
        y -= (1 - reveal) * 16;
        int left = Math.round(x), top = Math.round(y), width = Math.round(pw), height = Math.round(ph);
        fill(g, x + 5, y + 6, x + pw + 5, y + ph * .96f + 6, color(0x07120b, alpha * .28f));
        g.fillGradient(left, top, left + width, top + Math.round(ph * .96f), color(0xeee6d0, alpha * .96f), color(0xcfc5a8, alpha * .94f));
        int tip = Math.max(1, Math.round(ph * .04f));
        for (int row = 0; row < tip; row++) {
            int inset = Math.round(pw * .5f * row / tip);
            fill(g, x + inset, y + ph * .96f + row, x + pw - inset, y + ph * .96f + row + 1, color(0xcfc5a8, alpha * .94f));
        }
        for (int dx = 12; dx < width - 8; dx += 17)
            fill(g, left + dx, top + 4, left + dx + 1, top + height * .94f, color(0x6d7056, alpha * .035f));
        int ix = Math.round(pw * .09f), iy = Math.round(ph * .03f);
        g.renderOutline(left + ix, top + iy, width - ix * 2, Math.round(ph * .87f), color(0x6d7056, alpha * .30f));
        int[] glyphs = card.name().codePoints().toArray();
        float scale = Math.min(pw * .54f / 14, ph * .60f / (glyphs.length * 15.4f));
        float step = 15.4f * scale, start = y + ph * .10f;
        for (int i = 0; i < glyphs.length; i++) {
            String glyph = new String(Character.toChars(glyphs[i]));
            centered(g, CultivationInkTheme.brush(glyph), x + pw / 2, start + i * step,
                    scale, pw * .72f, 0x30382e, alpha, false);
        }
        centered(g, CultivationInkTheme.book(card.location().regionName() + " · 城镇"),
                x + pw / 2, y + ph * .79f, 1.6f, pw * .77f, 0x57604c, alpha, false);
        int sealSize = Math.round(Math.min(50, pw * .22f));
        int sealX = Math.round(x + (pw - sealSize) / 2), sealY = Math.round(y + ph * .86f);
        g.renderOutline(sealX, sealY, sealSize, sealSize, color(0x934a36, alpha));
        centered(g, CultivationInkTheme.book("境"), x + pw / 2, sealY + sealSize * .16f,
                sealSize / 14f, sealSize * .78f, 0x934a36, alpha, false);
    }

    private static void centered(GuiGraphics g, Component text, float x, float y, float scale,
            float maximumWidth, int rgb, float alpha, boolean shadow) {
        var font = Minecraft.getInstance().font;
        int width = font.width(text);
        scale = Math.min(scale, maximumWidth / Math.max(1, width));
        g.pose().pushPose();
        g.pose().translate(x - width * scale / 2, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color(rgb, alpha), shadow);
        g.pose().popPose();
    }
    private static int color(int rgb, float opacity) { return Math.round(Math.clamp(opacity, 0, 1) * 255) << 24 | rgb; }
    private static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), color);
    }
}
