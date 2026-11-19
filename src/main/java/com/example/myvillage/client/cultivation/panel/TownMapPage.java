package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.townmap.ClientTownMap;
import com.example.myvillage.client.townmap.TownMapData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import static com.example.myvillage.client.cultivation.panel.CultivationInkTheme.*;

public final class TownMapPage extends PanelPage {
    private static final int SIDEBAR = 120;
    private static final int GAP = 6;
    private static final int CARD = 0xFFF4EEDB;
    private static final int SELECTED = 0xFFD5DFC9;
    private static final int PLAYER = 0xFF2A686D;

    private record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private record Hit(Box box, TownMapData.Place place) { }

    private final List<Hit> hits = new ArrayList<>();
    private PanelButton action;
    private PanelButton stop;
    private int dockX, dockWidth;
    private ClientTownMap.View shown;
    private TownMapData.Place selected;
    private int mouseX = -1;
    private int mouseY = -1;
    private boolean scrollToTop;

    @Override public boolean requiresProfile() { return false; }

    private static boolean sideBySide(int width) { return width >= 230; }

    @Override
    public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        dockX = x;
        dockWidth = width;
        int buttonWidth = sideBySide(width) ? SIDEBAR : width;
        action = new PanelButton(PanelButton.Style.ACTION, JADE,
                x + width - buttonWidth, y + height - 21, buttonWidth, 19, Component.empty(), ignored -> act());
        action.setPrimary(true);
        widgets.accept(action);
        stop = new PanelButton(PanelButton.Style.ACTION, GOLD, x, y + height - 21, 62, 19,
                Component.literal("停止追踪"), ignored -> ClientTownMap.stop());
        widgets.accept(stop);
        return 23;
    }

    @Override
    public void refresh(PanelContext context, boolean visible) {
        if (action == null) return;
        action.visible = false;
        stop.visible = false;
        if (!visible) return;
        var current = ClientTownMap.view();
        var tracked = ClientTownMap.target();
        if (current != shown) {
            shown = current;
            selected = shown == null ? null : shown.data().places().getFirst();
            if (shown != null && tracked != null && shown.location().equals(tracked.location())) {
                selected = tracked.place();
            }
            scrollToTop = true;
        }
        boolean stopping = tracked != null && (shown == null || isTracking(selected));
        action.visible = selected != null || stopping;
        action.active = action.visible;
        stop.visible = tracked != null && !stopping;
        stop.active = stop.visible;
        int width = sideBySide(dockWidth) ? SIDEBAR : stop.visible ? dockWidth - stop.getWidth() - GAP : dockWidth;
        action.setWidth(Math.max(1, width));
        action.setX(dockX + dockWidth - action.getWidth());
        action.setPrimary(!stopping);
        action.setMessage(Component.literal(stopping ? "停止追踪" : tracked == null ? "追踪并返回游戏" : "改追此处并返回"));
    }

    private void act() {
        if (ClientTownMap.target() != null && (shown == null || isTracking(selected))) {
            ClientTownMap.stop();
        } else if (shown != null && selected != null && ClientTownMap.follow(shown, selected)) {
            var screen = Minecraft.getInstance().screen;
            if (screen != null) screen.onClose();
        }
    }

    private boolean isTracking(TownMapData.Place place) {
        var tracking = ClientTownMap.target();
        return shown != null && tracking != null && shown.location().equals(tracking.location()) && tracking.place().equals(place);
    }

    @Override public void pointer(int x, int y) { mouseX = x; mouseY = y; }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        for (var hit : hits.reversed()) {
            if (hit.box().contains(x, y)) {
                selected = hit.place();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean takeScrollToTop() {
        boolean result = scrollToTop;
        scrollToTop = false;
        return result;
    }

    @Override
    public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewport) {
        hits.clear();
        var font = c.font();
        if (shown == null) {
            title(g, font, "城镇地图", x, y + 3, width);
            text(g, font, ClientTownMap.nearby() ? "当前省城的地图资源无法读取。" : "到达省城附近后，这里会显示地图。",
                    x, y + 23, width, MUTED);
            status(g, font, x, y + 42, width);
            return 58;
        }

        title(g, font, shown.data().name(), x, y + 2, width);
        text(g, font, "点击地图或列表选择建筑", x, y + 20, width, MUTED);
        int bodyY = y + 36;
        boolean wide = sideBySide(width);
        int mapWidth = wide ? width - SIDEBAR - GAP : width;
        int mapHeight = wide ? Math.max(112, viewport - 52) : Math.min(152, Math.max(116, mapWidth));
        drawMap(g, font, new Box(x, bodyY, mapWidth, mapHeight));

        int detailsY = wide ? bodyY : bodyY + mapHeight + GAP;
        int detailsX = wide ? x + mapWidth + GAP : x;
        int bottom = details(g, font, detailsX, detailsY, wide ? SIDEBAR : width);
        bottom = Math.max(bodyY + mapHeight, bottom);
        status(g, font, x, bottom + 5, width);
        return bottom + 18 - y;
    }

    private void status(GuiGraphics g, Font f, int x, int y, int width) {
        var target = ClientTownMap.target();
        text(g, f, target == null ? "未追踪建筑" : "正在追踪 " + target.place().name(), x, y, width,
                target == null ? MUTED : GOLD);
    }

    private void drawMap(GuiGraphics g, Font f, Box frame) {
        var map = shown.data();
        int x = frame.x(), y = frame.y(), width = frame.w(), height = frame.h();
        g.fill(x, y, x + width, y + height, 0xFFDFDECA);
        g.renderOutline(x, y, width, height, RULE);
        double scale = Math.min((width - 8) / (double) map.width(), (height - 8) / (double) map.depth());
        if (scale <= 0) return;
        int mapW = Math.max(1, (int) Math.round(map.width() * scale));
        int mapH = Math.max(1, (int) Math.round(map.depth() * scale));
        int left = x + (width - mapW) / 2, top = y + (height - mapH) / 2;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(mapW / (float) map.textureWidth(), mapH / (float) map.textureHeight(), 1);
        g.blit(map.texture(), 0, 0, 0, 0, map.textureWidth(), map.textureHeight(), map.textureWidth(), map.textureHeight());
        pose.popPose();

        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            int px = mapPoint(mc.player.getX() - shown.location().origin().getX(), left, mapW, map.width());
            int py = mapPoint(mc.player.getZ() - shown.location().origin().getZ(), top, mapH, map.depth());
            g.fill(px - 3, py - 3, px + 4, py + 4, CARD);
            g.fill(px - 2, py - 2, px + 3, py + 3, PLAYER);
            double yaw = Math.toRadians(mc.player.getYRot());
            int dx = (int) Math.round(-Math.sin(yaw) * 4), dz = (int) Math.round(Math.cos(yaw) * 4);
            g.fill(px + dx - 1, py + dz - 1, px + dx + 2, py + dz + 2, PLAYER);
        }
        for (var place : map.places()) {
            if (!place.equals(selected)) pin(g, f, place, left, top, mapW, mapH);
        }
        pin(g, f, selected, left, top, mapW, mapH);

        int blocks = map.width() > 512 ? 128 : 32;
        int ruler = Math.max(2, (int) Math.round(blocks * scale));
        int tickX = x + 5, tickY = y + height - 7;
        g.fill(tickX, tickY, tickX + ruler, tickY + 1, INK);
        g.fill(tickX, tickY - 2, tickX + 1, tickY + 1, INK);
        g.fill(tickX + ruler - 1, tickY - 2, tickX + ruler, tickY + 1, INK);
        g.drawString(f, book(blocks + " 格"), tickX + ruler + 3, tickY - 5, INK, false);
        g.drawString(f, book("北"), x + width - 15, y + 4, INK, false);
        g.drawString(f, book("你的位置"), x + width - f.width(book("你的位置")) - 3, y + height - 12, PLAYER, false);
    }

    private static int mapPoint(double value, int start, int size, int blocks) {
        return start + Math.clamp((int) Math.floor(value * size / blocks), 0, size - 1);
    }

    private void pin(GuiGraphics g, Font f, TownMapData.Place place, int left, int top, int w, int h) {
        var map = shown.data();
        int x = mapPoint(place.entry().getX() + .5, left, w, map.width());
        int y = mapPoint(place.entry().getZ() + .5, top, h, map.depth());
        int bx = Math.clamp(x - 7, left - 2, left + Math.max(-2, w - 12));
        int by = Math.clamp(y - 16, top - 2, top + Math.max(-2, h - 12));
        Box label = new Box(bx, by, 14, 14);
        hits.add(new Hit(label, place));
        boolean active = place.equals(selected) || label.contains(mouseX, mouseY);
        g.fill(x - 2, y - 2, x + 3, y + 3, GOLD);
        g.fill(bx, by, bx + label.w(), by + label.h(), active ? JADE : CARD);
        g.renderOutline(bx, by, label.w(), label.h(), isTracking(place) ? GOLD : JADE);
        centered(g, f, place.service().glyph, bx, by + 3, label.w(), false, active ? PAPER : INK);
    }

    private int details(GuiGraphics g, Font f, int x, int y, int width) {
        int start = y;
        g.fill(x, y, x + width, y + 83, CARD);
        g.renderOutline(x, y, width, 83, RULE);
        text(g, f, "功能建筑", x + 5, y + 4, width - 10, INK);
        y += 16;
        var player = Minecraft.getInstance().player;
        for (var place : shown.data().places()) {
            var row = new Box(x + 3, y, width - 6, 16);
            hits.add(new Hit(row, place));
            if (place.equals(selected) || row.contains(mouseX, mouseY)) {
                g.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), SELECTED);
            }
            if (isTracking(place)) g.fill(row.x(), row.y(), row.x() + 2, row.y() + row.h(), GOLD);
            String distance = player == null ? "" : (long) Math.ceil(player.position()
                    .distanceTo(Vec3.atBottomCenterOf(shown.worldEntry(place)))) + " 格";
            pair(g, f, place.service().glyph + "  " + place.service().label, distance, x + 7, y + 4, width - 14,
                    isTracking(place) ? GOLD : INK);
            y += 16;
        }
        y = start + 91;
        text(g, f, selected.name(), x + 3, y, width - 6, INK);
        y += 14;
        for (var line : f.split(book(selected.service().description), Math.max(1, width - 6))) {
            g.drawString(f, line, x + 3, y, MUTED, false);
            y += 11;
        }
        return y;
    }
}
