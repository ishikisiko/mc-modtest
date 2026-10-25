package com.example.myvillage.client.story;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Existing text-only right HUD, with one separate world-target cue. No HUD fill, plate, or texture. */
final class StoryTracker {
    private StoryTracker() { }
    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "story_tracker"), StoryTracker::render);
    }
    private static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        var snapshot = ClientStory.snapshot();
        var mode = ClientStory.mode();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.screen != null || mc.options.hideGui || snapshot == null || !snapshot.available()
                || mode == StoryLayout.TrackerMode.HIDDEN) return;
        StoryMarker.render(g);
        var box = StoryLayout.tracker(g.guiWidth(), g.guiHeight());
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
            if (y + 10 <= box.bottom())
                g.drawString(font, CultivationInkTheme.fit(font, StoryClient.TRACKER.getTranslatedKeyMessage().getString()
                        + " 折叠/展开/隐藏 · " + StoryClient.JOURNAL.getTranslatedKeyMessage().getString() + " 行迹", box.width(), false),
                        box.x(), y, 0xFFC5CDBB, true);
        }
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
