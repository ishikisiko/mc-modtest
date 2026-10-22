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
    /** 身法闪避: the direction comes from the movement keys held when it is pressed. */
    public static final KeyMapping DODGE = new KeyMapping(
            "key.myvillage.dodge",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.myvillage");

    public static final KeyMapping ART_PRIMARY = new KeyMapping(
            "key.myvillage.art_primary", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.myvillage");
    public static final KeyMapping ART_SECONDARY = new KeyMapping(
            "key.myvillage.art_secondary", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.myvillage");

    public static final KeyMapping SKILL_I=new KeyMapping("key.myvillage.skill_i",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_I,"key.categories.myvillage");
    public static final KeyMapping SKILL_J=new KeyMapping("key.myvillage.skill_j",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,"key.categories.myvillage");
    public static final KeyMapping SKILL_K=new KeyMapping("key.myvillage.skill_k",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.myvillage");
    public static final KeyMapping SKILL_M=new KeyMapping("key.myvillage.skill_m",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.myvillage");
    public static final java.util.List<KeyMapping> SKILL_KEYS=java.util.List.of(ART_PRIMARY,ART_SECONDARY,SKILL_I,SKILL_J,SKILL_K,SKILL_M);

    public ClientCombatKeyMappings(IEventBus modEventBus) {
        ClientAdvancedArts.install(modEventBus);
        ClientSkillBindings.install();
        modEventBus.addListener(ClientCombatKeyMappings::registerKeyMappings);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_COMBAT_MODE);
        event.register(DODGE);
        event.register(ART_PRIMARY);
        event.register(ART_SECONDARY);
        event.register(SKILL_I);event.register(SKILL_J);event.register(SKILL_K);event.register(SKILL_M);
    }
}
