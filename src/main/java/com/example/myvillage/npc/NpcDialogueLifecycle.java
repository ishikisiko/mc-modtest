package com.example.myvillage.npc;

import com.example.myvillage.MyVillageMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** In-memory conversation capabilities never survive a player or server lifecycle boundary. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class NpcDialogueLifecycle {
    private NpcDialogueLifecycle() { }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        NpcDialogue.forget(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { NpcDialogue.clear(); }
    @SubscribeEvent public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) NpcDialogue.forget(player.getUUID());
    }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) { NpcDialogue.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        NpcDialogue.forget(event.getEntity().getUUID());
    }
}
