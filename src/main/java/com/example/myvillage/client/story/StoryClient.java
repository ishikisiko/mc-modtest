package com.example.myvillage.client.story;

import com.example.myvillage.MyVillageMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Independent, physical-client-only entry point; common bootstrap needs no client imports. */
@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class StoryClient {
    public static final KeyMapping TRACKER = new KeyMapping("key.yuanxian.story_tracker",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.yuanxian");
    public static final KeyMapping JOURNAL = new KeyMapping("key.yuanxian.story_journal",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.yuanxian");

    public StoryClient(IEventBus modBus) {
        ClientStory.install();
        NpcDialogueScreen.install();
        modBus.addListener(StoryClient::keys);
        modBus.addListener(StoryTracker::register);
        NeoForge.EVENT_BUS.addListener(ClientStory::tick);
        NeoForge.EVENT_BUS.addListener(ClientStory::logout);
        NeoForge.EVENT_BUS.addListener(StoryMarker::capture);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(TRACKER); event.register(JOURNAL); }
}
