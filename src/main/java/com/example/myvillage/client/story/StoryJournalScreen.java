package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.story.net.StorySnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O: read-only paper journal; every paragraph is scrollable, with no quest mutation controls. */
public final class StoryJournalScreen extends StoryPaperScreen {
    private StorySnapshotPayload snapshot;
    StoryJournalScreen(StorySnapshotPayload snapshot) { super(Component.literal("阡镇行迹")); this.snapshot = snapshot; }
    void update(StorySnapshotPayload next) { snapshot = next; rebuildWidgets(); }
    @Override protected void init() {
        List<String> paragraphs = new ArrayList<>();
        if (snapshot == null || !snapshot.available()) {
            paragraphs.add("此间尚无阡镇行迹。可先向阡镇的程砚问路。");
        } else {
            paragraphs.add(snapshot.completed() ? "本章已完" : snapshot.accepted() ? "当前行迹" : "尚未接取 · 可自行决定是否相助");
            paragraphs.add(snapshot.objective());
            if (!snapshot.hint().isBlank()) paragraphs.add(snapshot.hint());
            paragraphs.add("—— 已记之事 ——");
            paragraphs.addAll(snapshot.journal());
            if (snapshot.journal().isEmpty()) paragraphs.add("尚无记录。");
        }
        paper(0, paragraphs, true);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var p = layout.panel();
        CultivationInkTheme.title(g, font, "阡镇行迹", p.x() + 12, p.y() + 15, p.width() - 24);
        CultivationInkTheme.text(g, font, snapshot == null || snapshot.title().isBlank() ? "灯尽之前" : snapshot.title(),
                p.x() + 12, p.y() + 37, p.width() - 24, CultivationInkTheme.MUTED);
        g.fill(p.x() + 12, p.y() + 57, p.right() - 12, p.y() + 58, CultivationInkTheme.RULE);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (StoryClient.JOURNAL.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
