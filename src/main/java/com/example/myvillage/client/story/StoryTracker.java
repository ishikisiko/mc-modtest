package com.example.myvillage.client.story;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import com.example.myvillage.client.townmap.ClientTownMap;
import com.example.myvillage.story.net.StorySnapshotPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Right-side story and building readouts share a single world-target cue. */
final class StoryTracker {
    private StoryTracker() { }
    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "story_tracker"), StoryTracker::render);
    }
    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!StoryMarker.gameplayHudVisible()) return;
        var mc = Minecraft.getInstance();
        var snapshot = ClientStory.snapshot();
        var mode = ClientStory.mode();
        var building = ClientTownMap.targetInWorld();
        boolean arrival = com.example.myvillage.client.arrival.ArrivalClient.occupiesRightSide();
        var box = StoryLayout.tracker(g.guiWidth(), g.guiHeight());
        if (!arrival) {
            int tutorialBottom = OpeningGuidePreview.drawTracker(g, box);
            if (tutorialBottom > box.y()) {
                var previewTarget = OpeningGuidePreview.previewTarget();
                if (previewTarget != null) StoryMarker.render(g, previewTarget, box, tutorialBottom);
                return;
            }
        }
        boolean story = !arrival && snapshot != null && snapshot.available()
                && mode != StoryLayout.TrackerMode.HIDDEN;
        if (!story && building == null) return;

        int bottom = box.y();
        if (story) bottom = renderStory(g, delta, snapshot, mode, box);
        if (building != null && !arrival) {
            int y = story ? bottom + 6 : box.y();
            if (y + 24 <= box.bottom()) {
                var font = PanelFonts.get();
                String distance = (long) Math.ceil(mc.player.position().distanceTo(building.position())) + " 格";
                g.drawString(font, CultivationInkTheme.book("建筑追踪"), box.x(), y, 0xFFF4E4B4, true);
                var label = CultivationInkTheme.fit(font, building.place().name() + " · " + distance, box.width(), false);
                g.drawString(font, label, box.x(), y + 12, 0xFFFFF3C9, true);
                bottom = y + 24;
            }
        } else if (story) {
            bottom = box.y() + (mode == StoryLayout.TrackerMode.EXPANDED ? 155 : 65);
        }
        StoryMarker.render(g, building, box, bottom);
    }

    private static int renderStory(GuiGraphics g, DeltaTracker delta, StorySnapshotPayload snapshot,
                                   StoryLayout.TrackerMode mode, StoryLayout.Rect box) {
        var mc = Minecraft.getInstance();
        var font = PanelFonts.get();
        int y = box.y();
        Component title = CultivationInkTheme.fit(font, snapshot.title(), box.width(), true);
        g.drawString(font, title, box.x(), y, 0xFFF4E4B4, true);
        y += 17;
        int maxObjective = mode == StoryLayout.TrackerMode.EXPANDED ? 4 : 2;
        y = lines(g, snapshot.objective(), box, y, maxObjective, 0xFFF5F1DE);
        if (!snapshot.dimension().isEmpty()) {
            String distance = snapshot.dimension().equals(mc.level.dimension().location().toString())
                    ? "距目标 " + (long)Math.ceil(mc.player.position().distanceTo(
                        StoryMarker.target(snapshot, delta.getGameTimeDeltaPartialTick(false), false))) + " 格"
                    : "目标位于其他维度";
            g.drawString(font, CultivationInkTheme.fit(font, distance, box.width(), false), box.x(), y, 0xFFE0D2A6, true);
            y += 13;
        }
        if (mode == StoryLayout.TrackerMode.EXPANDED) {
            y += 3;
            y = lines(g, snapshot.hint(), box, y, 3, 0xFFE4E2CF);
            if (y + 10 <= box.bottom()) {
                g.drawString(font, CultivationInkTheme.fit(font, StoryClient.TRACKER.getTranslatedKeyMessage().getString()
                        + " 折叠/展开/隐藏 · " + StoryClient.JOURNAL.getTranslatedKeyMessage().getString() + " 行迹", box.width(), false),
                        box.x(), y, 0xFFC5CDBB, true);
                y += 12;
            }
        }
        return y;
    }
    private static int lines(GuiGraphics g, String text, StoryLayout.Rect box, int y, int maximum, int color) {
        var font = PanelFonts.get();
        var lines = font.split(CultivationInkTheme.book(text), box.width());
        for (int i = 0; i < Math.min(lines.size(), maximum) && y + 10 <= box.bottom(); i++) {
            if (i == maximum - 1 && lines.size() > maximum) {
                // Continuation is explicit; the journal keeps the full server-authored text.
                StringBuilder row = new StringBuilder();
                lines.get(i).accept((index, style, codePoint) -> { row.appendCodePoint(codePoint); return true; });
                g.drawString(font, CultivationInkTheme.fit(font, row + "…", box.width(), false), box.x(), y, color, true);
            } else g.drawString(font, lines.get(i), box.x(), y, color, true);
            y += 12;
        }
        return y;
    }
}
