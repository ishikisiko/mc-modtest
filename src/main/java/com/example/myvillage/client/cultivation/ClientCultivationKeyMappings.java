package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCultivationKeyMappings {
    public static final KeyMapping OPEN_PROFILE = new KeyMapping(
            "key.myvillage.open_cultivation_profile",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.myvillage");
    public static final KeyMapping START_NORMAL_MEDITATION = new KeyMapping(
            "key.myvillage.start_normal_meditation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.myvillage");
    public static final KeyMapping START_SPIRIT_MEDITATION = new KeyMapping(
            "key.myvillage.start_spirit_meditation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.myvillage");
    public static final KeyMapping STOP_MEDITATION = new KeyMapping(
            "key.myvillage.stop_meditation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.myvillage");
    public static final KeyMapping START_ADVANCEMENT = new KeyMapping(
            "key.myvillage.start_advancement",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.myvillage");

    public ClientCultivationKeyMappings(IEventBus modEventBus) {
        modEventBus.addListener(ClientCultivationKeyMappings::registerKeyMappings);
    }

    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROFILE);
        event.register(START_NORMAL_MEDITATION);
        event.register(START_SPIRIT_MEDITATION);
        event.register(STOP_MEDITATION);
        event.register(START_ADVANCEMENT);
    }
}
