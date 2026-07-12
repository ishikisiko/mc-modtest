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

    public ClientCultivationKeyMappings(IEventBus modEventBus) {
        modEventBus.addListener(ClientCultivationKeyMappings::registerKeyMappings);
    }

    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROFILE);
    }
}
