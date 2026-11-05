package com.example.myvillage.client.talisman;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 只观察实际客户端物品、界面和输入状态，不触发操作。 */
@EventBusSubscriber(modid = "myvillage", value = Dist.CLIENT)
public final class TravelTalismanClientProbe {
    private static String previous = "";
    @SubscribeEvent static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var p = mc.player; var j = new JsonObject();
        j.addProperty("held", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
        j.addProperty("selected", p.getInventory().selected);
        j.addProperty("dimension", mc.level.dimension().location().toString());
        j.addProperty("x", Math.rint(p.getX() * 1000) / 1000); j.addProperty("z", Math.rint(p.getZ() * 1000) / 1000);
        j.addProperty("onGround", p.onGround()); j.addProperty("shift", mc.options.keyShift.isDown());
        j.addProperty("screen", mc.screen == null ? "game" : mc.screen.getClass().getSimpleName());
        j.addProperty("focused", mc.isWindowActive());
        String value = j.toString();
        if (!value.equals(previous)) {
            org.slf4j.LoggerFactory.getLogger(TravelTalismanClientProbe.class).info("TRAVEL_CLIENT_STATE {}", value);
            previous = value;
        }
    }
}
