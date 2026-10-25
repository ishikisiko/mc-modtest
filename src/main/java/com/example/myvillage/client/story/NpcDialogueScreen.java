package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.portrait.PortraitTextures;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.npc.NpcDialogueIntent;
import com.example.myvillage.npc.NpcDialoguePayload;
import com.example.myvillage.story.net.StoryDialoguePayload;
import com.example.myvillage.story.net.StoryIntentPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Common conversation, using the established paper layout and server-authored options. */
public final class NpcDialogueScreen extends StoryPaperScreen {
    private StoryDialoguePayload page;
    private final UUID entity;
    private final int person;
    private final ClientLevel world;
    private final LocalPlayer viewer;
    private final List<StoryOptionButton> buttons = new ArrayList<>();
    private boolean closeSent;
    private boolean dismissing;

    private NpcDialogueScreen(StoryDialoguePayload page, NpcEntity npc) {
        super(Component.literal(page.title()));
        this.page = page; entity = npc.getUUID(); person = npc.ledgerPersonId();
        world = Minecraft.getInstance().level; viewer = Minecraft.getInstance().player;
    }
    public static void install() {
        NpcDialoguePayload.installReceiver(NpcDialogueScreen::receive);
        NeoForge.EVENT_BUS.addListener(NpcDialogueScreen::tick);
        NeoForge.EVENT_BUS.addListener(NpcDialogueScreen::logout);
    }
    private static NpcEntity npc(int id) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()) return null;
        return mc.level.getEntity(id) instanceof NpcEntity n && n.isAlive() && !n.isRemoved()
                && !n.combatEngaged() && n.distanceToSqr(mc.player) <= 100 ? n : null;
    }
    private boolean valid() {
        var mc = Minecraft.getInstance(); var npc = npc(page.entityId());
        return mc.level == world && mc.player == viewer && npc != null
                && npc.getUUID().equals(entity) && npc.ledgerPersonId() == person;
    }
    private static void receive(NpcDialoguePayload payload) {
        var mc = Minecraft.getInstance(); var page = payload.page(); var npc = npc(page.entityId());
        if (npc == null) return;
        if (mc.screen instanceof NpcDialogueScreen open) {
            if (open.valid() && open.entity.equals(npc.getUUID())) {
                open.page = page; open.closeSent = false; open.rebuildWidgets();
            } else {
                open.dismiss(); mc.setScreen(new NpcDialogueScreen(page, npc));
            }
        } else if (mc.screen == null) mc.setScreen(new NpcDialogueScreen(page, npc));
    }
    private static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen instanceof NpcDialogueScreen open && !open.valid()) open.dismiss();
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Minecraft.getInstance().screen instanceof NpcDialogueScreen open) open.dismiss();
    }
    @Override protected void init() {
        paper(page.options().size(), page.lines(), false);
        buttons.clear();
        for (int i = 0; i < page.options().size(); i++) {
            var option = page.options().get(i);
            buttons.add(addRenderableWidget(new StoryOptionButton(layout.option(i), option.text(), ignored -> choose(option.id()))));
        }
    }
    private void choose(String option) {
        if (option.equals("close")) { onClose(); return; }
        if (!valid()) { dismiss(); return; }
        PacketDistributor.sendToServer(new NpcDialogueIntent(new StoryIntentPayload(page.entityId(), page.nonce(), option)));
        for (var button : buttons) button.active = false;
        if (option.equals("trade") || option.equals("story") || option.equals("sect_office")) dismiss();
    }
    private void sendClose() {
        if (!closeSent && !dismissing && minecraft != null && minecraft.getConnection() != null) {
            closeSent = true;
            PacketDistributor.sendToServer(new NpcDialogueIntent(new StoryIntentPayload(page.entityId(), page.nonce(), "close")));
        }
    }
    private void dismiss() { dismissing = true; if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null); }
    @Override public void onClose() { sendClose(); super.onClose(); }
    @Override public void removed() { sendClose(); super.removed(); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var p = layout.panel();
        PortraitTextures.draw(g, page.portrait(), p.x() + 12, p.y() + 12, 44);
        CultivationInkTheme.title(g, font, page.name(), p.x() + 66, p.y() + 13, p.width() - 78);
        CultivationInkTheme.text(g, font, page.title(), p.x() + 66, p.y() + 31, p.width() - 78, CultivationInkTheme.INK);
        CultivationInkTheme.text(g, font, page.role(), p.x() + 66, p.y() + 46, p.width() - 78, CultivationInkTheme.MUTED);
        g.fill(p.x() + 12, p.y() + 61, p.right() - 12, p.y() + 62, CultivationInkTheme.RULE);
    }
}
