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
    public static final KeyMapping TRACKER = new KeyMapping("key.myvillage.story_tracker",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.myvillage");
    public static final KeyMapping JOURNAL = new KeyMapping("key.myvillage.story_journal",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.myvillage");

    public StoryClient(IEventBus modBus) {
        ClientStory.install();
        modBus.addListener(StoryClient::keys);
        modBus.addListener(StoryTracker::register);
        NeoForge.EVENT_BUS.addListener(ClientStory::tick);
        NeoForge.EVENT_BUS.addListener(ClientStory::logout);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(TRACKER); event.register(JOURNAL); }
}
