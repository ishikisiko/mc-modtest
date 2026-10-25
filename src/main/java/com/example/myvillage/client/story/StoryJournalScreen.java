package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.story.net.StorySnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O: paper journal with bounded personal realm travel; quest acceptance and rewards stay with NPC dialogue. */
public final class StoryJournalScreen extends StoryPaperScreen {
    private StorySnapshotPayload snapshot;
    StoryJournalScreen(StorySnapshotPayload snapshot) { super(Component.literal("阡镇行迹")); this.snapshot = snapshot; }
    void update(StorySnapshotPayload next) { snapshot = next; rebuildWidgets(); }
    @Override protected void init() {
        List<String> paragraphs = new ArrayList<>();
        if (snapshot == null || !snapshot.available()) {
            paragraphs.add("此间尚无阡镇行迹。进入阡镇后，可向程砚问路，或去药庐找沈岚。");
        } else {
            paragraphs.add(snapshot.completed() ? "本章已完" : snapshot.accepted() ? "当前行迹" : "尚未接取 · 可自行决定是否相助");
            paragraphs.add(snapshot.objective());
            if (!snapshot.hint().isBlank()) paragraphs.add(snapshot.hint());
            paragraphs.add("—— 已记之事 ——");
            paragraphs.addAll(snapshot.journal());
            if (snapshot.journal().isEmpty()) paragraphs.add("尚无记录。");
        }
        boolean inside = minecraft != null && minecraft.level != null && minecraft.player != null
                && com.example.myvillage.story.realm.RealmIdentity.owner(minecraft.level.dimension().location().toString()).filter(minecraft.player.getUUID()::equals).isPresent();
        boolean legacy = snapshot != null && snapshot.accepted() && !snapshot.dimension().isEmpty()
                && !com.example.myvillage.story.realm.RealmIdentity.reserved(snapshot.dimension());
        boolean travel = minecraft != null && minecraft.player != null && !legacy;
        paper(travel ? 1 : 0, paragraphs, true);
        if (travel) {
            var box=layout.option(0);
            double sx=minecraft.getWindow().getWidth()/(double)width, sy=minecraft.getWindow().getHeight()/(double)height;
            org.slf4j.LoggerFactory.getLogger(StoryJournalScreen.class).info("QIANZHEN_JOURNAL action={} x={} y={} w={} h={}", inside?"leave":"enter",
                    Math.round(box.x()*sx),Math.round(box.y()*sy),Math.round(box.width()*sx),Math.round(box.height()*sy));
        }
        if (travel) addRenderableWidget(new StoryOptionButton(layout.option(0), inside ? "返回原世界" : "进入阡镇", ignored -> {
            if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(inside ? "qianzhen leave" : "qianzhen enter");
            onClose();
        }));
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
