package com.example.myvillage.client.story;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.story.net.StoryDialoguePayload;
import com.example.myvillage.story.net.StorySnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client presentation only. Never calculates an objective, evidence, reward, or next story stage. */
public final class ClientStory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStory.class);
    private static ClientLevel level;
    private static StorySnapshotPayload snapshot;
    private static StoryLayout.TrackerMode mode = StoryLayout.TrackerMode.COMPACT;
    private ClientStory() { }
    static void install() {
        StorySnapshotPayload.installReceiver(ClientStory::receiveSnapshot);
        StoryDialoguePayload.installReceiver(ClientStory::receiveDialogue);
    }
    static StorySnapshotPayload snapshot() {
        return level == Minecraft.getInstance().level ? snapshot : null;
    }
    static StoryLayout.TrackerMode mode() { return mode; }

    /** Bind before receiving as well as ticking, so a first snapshot after dimension travel survives. */
    private static boolean currentWorld() {
        var mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        return mc.level != null && mc.player != null;
    }
    public static void clear() {
        snapshot = null;
        level = null;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof StoryDialogueScreen dialogue) dialogue.dismiss();
        else if (mc.screen instanceof StoryJournalScreen) mc.setScreen(null);
    }
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        mode = StoryLayout.TrackerMode.COMPACT;
    }
    private static void receiveSnapshot(StorySnapshotPayload next) {
        if (!currentWorld()) { clear(); return; }
        if (snapshot != null && next.revision() < snapshot.revision()) return;
        snapshot = next;
        LOGGER.info("QIANZHEN_STORY_VIEW revision={} available={} accepted={} completed={} chapter={} title={} objective={}",
                next.revision(), next.available(), next.accepted(), next.completed(), next.chapter(), next.title(), next.objective());
        var mc = Minecraft.getInstance();
        if (!next.available() && mc.screen instanceof StoryDialogueScreen dialogue) dialogue.dismiss();
        if (mc.screen instanceof StoryJournalScreen journal) journal.update(next);
    }
    private static NpcEntity avatar(int entityId) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()) return null;
        var entity = mc.level.getEntity(entityId);
        if (!(entity instanceof NpcEntity npc) || !npc.isAlive() || npc.isRemoved() || !npc.isLedgerAvatar()
                || npc.distanceToSqr(mc.player) > 100) return null;
        return npc;
    }
    static boolean valid(StoryDialogueScreen open) {
        var npc = avatar(open.entityId());
        return npc != null && npc.getUUID().equals(open.avatar()) && npc.ledgerPersonId() == open.personId();
    }
    private static void receiveDialogue(StoryDialoguePayload page) {
        if (!currentWorld()) { clear(); return; }
        var npc = avatar(page.entityId());
        if (npc == null) {
            LOGGER.warn("QIANZHEN_DIALOGUE rejected stale/invalid entity={}", page.entityId());
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof StoryDialogueScreen open) {
            if (!valid(open)) {
                open.dismiss();
                mc.setScreen(new StoryDialogueScreen(page, npc.getUUID(), npc.ledgerPersonId()));
            } else if (open.entityId() == page.entityId() && open.avatar().equals(npc.getUUID())) open.update(page);
            else mc.setScreen(new StoryDialogueScreen(page, npc.getUUID(), npc.ledgerPersonId()));
        } else if (mc.screen == null) {
            mc.setScreen(new StoryDialogueScreen(page, npc.getUUID(), npc.ledgerPersonId()));
        }
    }
    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!currentWorld()) { if (snapshot != null) clear(); return; }
        // A rescued actor can be withdrawn while the player's next objective remains valid.
        // Body/session invalidation must not erase the freshly received chapter snapshot.
        if (mc.screen instanceof StoryDialogueScreen open && !valid(open)) open.dismiss();
        while (StoryClient.TRACKER.consumeClick()) {
            if (mc.screen == null) {
                mode = mode.next();
                LOGGER.info("QIANZHEN_STORY_TRACKER mode={}", mode);
            }
        }
        while (StoryClient.JOURNAL.consumeClick()) {
            if (mc.screen == null && mc.player.isAlive()) mc.setScreen(new StoryJournalScreen(snapshot));
        }
    }
}
