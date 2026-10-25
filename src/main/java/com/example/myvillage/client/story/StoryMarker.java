package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.story.net.StorySnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;

/** One current, server-disclosed objective. HUD projection intentionally remains visible through walls. */
final class StoryMarker {
    private static final int GOLD = 0xFFF3D894, LIGHT = 0xFFFFF3C9, OUTLINE = 0xE51A261F;
    private record Frame(ClientLevel level, StorySnapshotPayload snapshot, float x, float y, float w, double distance) { }
    private static Frame frame;
    private StoryMarker() { }
    static void clear() { frame = null; }

    static boolean eligible(StorySnapshotPayload snapshot) {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.screen == null && !mc.options.hideGui && ClientStory.mode() != StoryLayout.TrackerMode.HIDDEN
                && snapshot != null && snapshot.available() && !snapshot.completed() && !snapshot.dimension().isEmpty()
                && snapshot.dimension().equals(mc.level.dimension().location().toString());
    }

    /** Live loaded body wins over the server's fallback. No role names or authored old-rescue coordinates. */
    static Vec3 target(StorySnapshotPayload snapshot, float partialTick, boolean above) {
        var mc = Minecraft.getInstance();
        if (snapshot.targetPersonId() >= 0 && mc.level != null) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof NpcEntity npc && npc.isLedgerAvatar() && npc.isAlive() && !npc.isRemoved()
                        && npc.ledgerPersonId() == snapshot.targetPersonId()) {
                    return npc.getPosition(partialTick).add(0, above ? npc.getBbHeight() + .4 : 0, 0);
                }
            }
        }
        // Actor fallback is a server-updated foot position; fixed points denote the clicked block centre.
        return Vec3.atCenterOf(snapshot.target()).add(0,
                above && snapshot.targetPersonId() >= 0 ? 1.7 : 0, 0);
    }

    static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        frame = null;
        var snapshot = ClientStory.snapshot();
        if (!eligible(snapshot)) return;
        var mc = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var position = target(snapshot, partial, true).subtract(event.getCamera().getPosition());
        var clip = new Vector4f((float)position.x, (float)position.y, (float)position.z, 1);
        event.getModelViewMatrix().transform(clip);
        event.getProjectionMatrix().transform(clip);
        frame = new Frame(mc.level, snapshot, clip.x, clip.y, clip.w,
                mc.player.position().distanceTo(target(snapshot, partial, false)));
    }

    static void render(GuiGraphics g) {
        var snapshot = ClientStory.snapshot();
        var current = frame;
        if (!eligible(snapshot) || current == null || current.level() != Minecraft.getInstance().level
                || current.snapshot() != snapshot) return;
        var point = StoryMarkerProjection.project(current.x(), current.y(), current.w(), g.guiWidth(), g.guiHeight());
        if (point == null) return;
        int x = (int)Math.round(point.x()), y = (int)Math.round(point.y());
        var tracker = StoryLayout.tracker(g.guiWidth(), g.guiHeight());
        // Keep the cue outside the text block; an overlap becomes a directional cue toward its true point.
        int trackerBottom = tracker.y() + (ClientStory.mode() == StoryLayout.TrackerMode.EXPANDED ? 155 : 65);
        boolean overlapsTracker = x >= tracker.x() - 12 && y >= tracker.y() - 12 && y < trackerBottom;
        boolean directional = point.edge() || overlapsTracker;
        double directionX = point.directionX(), directionY = point.directionY();
        if (overlapsTracker) {
            int trueX = x;
            x = tracker.x() - 16;
            if (!point.edge()) { directionX = Math.signum(trueX - x); directionY = 0; }
        }
        diamond(g, x, y, 6, OUTLINE);
        diamond(g, x, y, 4, GOLD);
        g.fill(x - 1, y - 1, x + 2, y + 2, LIGHT);
        if (directional) {
            int tipX = x + (int)Math.round(directionX * 11);
            int tipY = y + (int)Math.round(directionY * 11);
            double px = -directionY, py = directionX;
            line(g, tipX, tipY, tipX - (int)Math.round(directionX * 4 + px * 3),
                    tipY - (int)Math.round(directionY * 4 + py * 3), GOLD);
            line(g, tipX, tipY, tipX - (int)Math.round(directionX * 4 - px * 3),
                    tipY - (int)Math.round(directionY * 4 - py * 3), GOLD);
        } else g.fill(x, y + 7, x + 1, y + 10, GOLD);

        String distance = (long)Math.ceil(current.distance()) + " 格";
        String label = !directional && current.distance() <= 24 && !snapshot.targetLabel().isBlank()
                ? snapshot.targetLabel() + " · " + distance : distance;
        var font = PanelFonts.get();
        int maxWidth = Math.min(126, g.guiWidth() - 40);
        var text = CultivationInkTheme.fit(font, label, maxWidth, false);
        int textWidth = font.width(text);
        int tx = Math.max(8, Math.min(g.guiWidth() - textWidth - 8, x - textWidth / 2));
        int ty = y < 40 ? y + 15 : y - 21;
        if (tx + textWidth < tracker.x() - 4 || ty < tracker.y() - 12 || ty > trackerBottom)
            g.drawString(font, text, tx, ty, LIGHT, true);
    }

    private static void diamond(GuiGraphics g, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = radius - Math.abs(dy);
            g.fill(x - dx, y + dy, x - dx + 1, y + dy + 1, color);
            g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
        }
    }
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x0 : x0 + (int)Math.round((x1 - x0) * i / (double)steps);
            int y = steps == 0 ? y0 : y0 + (int)Math.round((y1 - y0) * i / (double)steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
