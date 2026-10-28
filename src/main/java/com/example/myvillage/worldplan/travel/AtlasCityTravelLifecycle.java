package com.example.myvillage.worldplan.travel;

import com.example.myvillage.MyVillageMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Independent registration avoids changing the world-plan/version author's lifecycle. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class AtlasCityTravelLifecycle {
    private AtlasCityTravelLifecycle() { }
    @SubscribeEvent public static void started(ServerStartedEvent event) { AtlasCityTravelService.started(event.getServer()); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { AtlasCityTravelService.tick(event.getServer()); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { AtlasCityTravelService.stop(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) AtlasCityTravelService.forget(event.getEntity().getUUID(),"logout");
    }
    @SubscribeEvent public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) AtlasCityTravelService.forget(event.getEntity().getUUID(),"death");
    }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer) AtlasCityTravelService.forget(event.getEntity().getUUID(),"clone");
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer) AtlasCityTravelService.forget(event.getEntity().getUUID(),"dimension_changed");
    }
}
