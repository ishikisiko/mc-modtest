package com.example.myvillage.client.tribulation;

import com.example.myvillage.client.cultivation.ClientCultivationKeyMappings;
import com.example.myvillage.cultivation.tribulation.TribulationStatusPayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** 所有者专用局部劫云、雷弧、固定落点与状态条；不生成实体、不改天气、不提交命中。 */
@EventBusSubscriber(modid = "myvillage", value = Dist.CLIENT)
public final class TribulationClient {
    private static TribulationStatusPayload status;
    private static long ticks, receivedAt;
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private record Bolt(TribulationStatusPayload.Mark mark, double top, long born, int mainNumber) { }
    private static final RenderType GLOW = RenderType.create("myvillage_tribulation",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private TribulationClient() { }

    @EventBusSubscriber(modid = "myvillage", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            TribulationStatusPayload.installReceiver(TribulationClient::receive);
            event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.parse("myvillage:tribulation"), TribulationClient::hud);
        }
    }
    private static void receive(TribulationStatusPayload p) {
        if (status != null && !status.attempt().equals(p.attempt())) BOLTS.clear();
        status = p; receivedAt = ticks;
        for (var m : p.marks()) if (m.impact()) BOLTS.add(new Bolt(m, p.y() + p.cloudHeight(), ticks, p.mainCompleted()));
    }
    public static boolean active() { return status != null && (status.phase().equals("PREPARING") || status.phase().equals("ACTIVE")); }
    public static boolean preparing() { return active() && status.preparation() > 0; }
    public static boolean recovering() { return status != null && status.phase().equals("RECOVERING"); }
    @SubscribeEvent static void logout(ClientPlayerNetworkEvent.LoggingOut event) { status = null; BOLTS.clear(); ticks = receivedAt = 0; }
    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        ticks++;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { status = null; BOLTS.clear(); return; }
        BOLTS.removeIf(b -> ticks - b.born() >= 10);
        if (status == null) return;
        if (!active() && !recovering() && ticks - receivedAt > 120) { status = null; return; }
        if (!active() || ticks % status.particleInterval() != 0) return;
        // 十二朵局部烟云，预算不随世界玩家数/区块数扩大。
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6 + ticks * .007;
            double radius = status.radius() * .55;
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE, status.x() + Math.cos(angle) * radius,
                    status.y() + status.cloudHeight(), status.z() + Math.sin(angle) * radius, 0, .005, 0);
        }
    }
    private static void hud(GuiGraphics g, DeltaTracker delta) {
        if (status == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        int width = Math.min(310, g.guiWidth() - 16), x = (g.guiWidth() - width) / 2, y = 52;
        int accent = status.boundaryWarning() ? 0xFFEBA99A : 0xFFD9C4F0;
        g.fill(x, y, x + width, y + 49, 0xDB181C29);
        g.fill(x, y, x + 2, y + 49, accent);
        String title, detail;
        if (status.preparation() > 0 && active()) {
            title = "金丹雷劫 · 准备 " + seconds(status.preparation()) + " 秒";
            detail = "失败损 " + status.failureStabilityLoss() + " 稳定；保境界修为，不返耗材";
        } else if (active()) {
            int strike = BOLTS.stream().filter(b -> b.mark().main()).mapToInt(Bolt::mainNumber).max()
                    .orElse(Math.min(6, status.mainCompleted() + 1));
            title = "金丹雷劫 · 第 " + strike + " 道 / 6 · 第 " + ((strike + 1) / 2) + " 轮";
            detail = "已过 " + status.mainCompleted() + " 道 · 余 " + seconds(status.remaining()) + " 秒 · 可用符丹与防御";
        } else {
            title = status.result();
            detail = recovering() ? "调息 " + seconds(status.recovery()) + " 秒；稳定度损失已结算" : "本次雷劫已结算";
        }
        drawFit(g, title, x + 9, y + 7, width - 18, accent);
        drawFit(g, detail, x + 9, y + 21, width - 18, 0xFFE8E2D5);
        String hint = status.boundaryWarning() ? "已出界！" + seconds(status.outsideRemaining()) + " 秒后失败，请回到光圈内"
                : preparing() ? "初次准备可按 [" + ClientCultivationKeyMappings.STOP_MEDITATION.getTranslatedKeyMessage().getString() + "] 收功取消；重登将继续原劫"
                : active() ? "主雷不可闪免；余雷落点固定，离圈可躲" : "生命、灵力不因结算补满";
        drawFit(g, hint, x + 9, y + 35, width - 18, status.boundaryWarning() ? 0xFFFF9C89 : 0xFFB9C8D8);
    }
    private static int seconds(int ticks) { return (ticks + 19) / 20; }
    private static void drawFit(GuiGraphics g, String text, int x, int y, int max, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, font.plainSubstrByWidth(text, max), x, y, color, false);
    }
    @SubscribeEvent static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || status == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(GLOW);
        if (active()) {
            ring(out, new Vec3(status.x(), status.y() + .06, status.z()).subtract(camera), status.radius(), .07, status.points(), 0x7DB4CEE6);
            // 劫云为局部低透明多层盘形，与世界云/天气独立。
            for (int layer = 0; layer < 3; layer++)
                ring(out, new Vec3(status.x(), status.y() + status.cloudHeight() + layer * .15, status.z()).subtract(camera),
                        status.radius() * (.55 - layer * .10), status.radius() * .15, status.points(), 0x90384058);
            for (var mark : status.marks()) if (!mark.impact()) {
                Vec3 origin = new Vec3(mark.x(), mark.y() + .10, mark.z()).subtract(camera);
                ring(out, origin, mark.radius(), .12, status.points(), mark.main() ? 0xD7D1A5FC : 0xE8F0C578);
                ring(out, origin, mark.radius() * .8, .04, status.points(), mark.main() ? 0x90D1A5FC : 0x90F0C578);
            }
        }
        for (var bolt : BOLTS) {
            Vec3 bottom = new Vec3(bolt.mark().x(), bolt.mark().y(), bolt.mark().z()).subtract(camera);
            double height = bolt.top() - bolt.mark().y();
            // 两个交叉面构成有限折线雷弧，既无 LightningBolt，也无全屏闪光。
            for (int i = 0; i < 12; i++) {
                double t = i / 12.0, u = (i + 1) / 12.0;
                double a = i == 0 ? 0 : Math.sin(i * 9.3) * .65;
                double b = i == 11 ? 0 : Math.sin((i + 1) * 9.3) * .65;
                Vec3 from = bottom.add(a, height * t, a * .5), to = bottom.add(b, height * u, b * .5);
                ribbon(out, from, to, .16, 0xF0D3B0FF, false);
                ribbon(out, from, to, .055, 0xFFF8F2FF, false);
                ribbon(out, from, to, .12, 0xE6D3B0FF, true);
            }
        }
        buffers.endBatch(GLOW);
    }
    private static void ring(VertexConsumer out, Vec3 center, double r, double width, int points, int color) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points, b = (i + 1) * Math.PI * 2 / points;
            vertex(out, center.add(Math.cos(a) * r, 0, Math.sin(a) * r), color);
            vertex(out, center.add(Math.cos(b) * r, 0, Math.sin(b) * r), color);
            vertex(out, center.add(Math.cos(b) * (r - width), 0, Math.sin(b) * (r - width)), color);
            vertex(out, center.add(Math.cos(a) * (r - width), 0, Math.sin(a) * (r - width)), color);
        }
    }
    private static void ribbon(VertexConsumer out, Vec3 a, Vec3 b, double width, int color, boolean cross) {
        Vec3 offset = cross ? new Vec3(0, 0, width) : new Vec3(width, 0, 0);
        vertex(out, a.add(offset), color); vertex(out, b.add(offset), color);
        vertex(out, b.subtract(offset), color); vertex(out, a.subtract(offset), color);
    }
    private static void vertex(VertexConsumer out, Vec3 p, int c) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24);
    }
}
