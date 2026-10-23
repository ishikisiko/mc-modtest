package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.portrait.PortraitTextures;
import com.example.myvillage.story.net.StoryDialoguePayload;
import com.example.myvillage.story.net.StoryIntentPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One live avatar session. Updating its server page never calls Minecraft.setScreen again. */
public final class StoryDialogueScreen extends StoryPaperScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryDialogueScreen.class);
    private StoryDialoguePayload page;
    private final UUID avatar;
    private final int personId;
    private final List<StoryOptionButton> buttons = new ArrayList<>();
    private boolean preserveScroll;
    private boolean closeSent;
    private boolean dismissing;

    StoryDialogueScreen(StoryDialoguePayload page, UUID avatar, int personId) {
        super(Component.literal(page.title()));
        this.page = page;
        this.avatar = avatar;
        this.personId = personId;
    }
    public int entityId() { return page.entityId(); }
    UUID avatar() { return avatar; }
    int personId() { return personId; }

    public void update(StoryDialoguePayload next) {
        preserveScroll = page.lines().equals(next.lines());
        page = next;
        closeSent = false;
        rebuildWidgets();
    }

    @Override protected void init() {
        paper(page.options().size(), page.lines(), preserveScroll);
        preserveScroll = true;
        buttons.clear();
        for (int i = 0; i < page.options().size(); i++) {
            var option = page.options().get(i);
            var box = layout.option(i);
            var button = new StoryOptionButton(box, option.text(), ignored -> choose(option.id()));
            buttons.add(addRenderableWidget(button));
            // Framebuffer physical pixels, including GUI rounding at non-integral scales.
            double sx = minecraft.getWindow().getWidth() / (double)width;
            double sy = minecraft.getWindow().getHeight() / (double)height;
            LOGGER.info("QIANZHEN_DIALOGUE option={} x={} y={} w={} h={} nonce={}", option.id(),
                    Math.round(box.x() * sx), Math.round(box.y() * sy), Math.round(box.width() * sx),
                    Math.round(box.height() * sy), page.nonce());
        }
    }
    private void choose(String option) {
        if (option.equals("close")) { onClose(); return; }
        if (!ClientStory.valid(this)) { ClientStory.clear(); return; }
        PacketDistributor.sendToServer(new StoryIntentPayload(page.entityId(), page.nonce(), option));
        // Prevent accidental repeated clicks while waiting for the next rotated nonce.
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).active = page.options().get(i).id().equals("close");
    }
    private void sendClose() {
        if (!closeSent && !dismissing && minecraft != null && minecraft.getConnection() != null) {
            closeSent = true;
            PacketDistributor.sendToServer(new StoryIntentPayload(page.entityId(), page.nonce(), "close"));
        }
    }
    /** Invalid/old entity or level: clear locally without submitting an intent for that entity. */
    void dismiss() { dismissing = true; if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null); }
    @Override public void onClose() { sendClose(); super.onClose(); }
    @Override public void removed() { sendClose(); super.removed(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var p = layout.panel();
        PortraitTextures.draw(g, page.portrait(), p.x() + 12, p.y() + 12, 44);
        CultivationInkTheme.title(g, font, page.name(), p.x() + 66, p.y() + 13, p.width() - 78);
        CultivationInkTheme.text(g, font, page.title(), p.x() + 66, p.y() + 31, p.width() - 78, CultivationInkTheme.INK);
        CultivationInkTheme.text(g, font, role(page.role()), p.x() + 66, p.y() + 46, p.width() - 78, CultivationInkTheme.MUTED);
        g.fill(p.x() + 12, p.y() + 61, p.right() - 12, p.y() + 62, CultivationInkTheme.RULE);
    }
    private static String role(String role) {
        return switch (role) {
            case "guide" -> "引路人";
            case "pharmacist" -> "药师";
            case "zhou_he" -> "年轻采药人 · 初入炼气";
            case "warden" -> "守阵人";
            default -> "阡镇来客";
        };
    }
}
