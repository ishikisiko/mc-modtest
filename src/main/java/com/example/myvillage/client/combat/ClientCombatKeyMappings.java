package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCombatKeyMappings {
    public static final KeyMapping TOGGLE_COMBAT_MODE = new KeyMapping(
            "key.myvillage.toggle_combat_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.myvillage");

    public ClientCombatKeyMappings(IEventBus modEventBus) {
        modEventBus.addListener(ClientCombatKeyMappings::registerKeyMappings);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_COMBAT_MODE);
    }
}
