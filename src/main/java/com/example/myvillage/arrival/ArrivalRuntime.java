package com.example.myvillage.arrival;

import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Presentation notices only. No persistent exploration or gameplay progress is changed. */
public final class ArrivalRuntime {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Map<UUID, ArrivalTracker> PLAYERS = new HashMap<>();
    private ArrivalRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArrivalRuntime::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> PLAYERS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> PLAYERS.clear());
    }

    public static ArrivalLocation location(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return ArrivalLocation.EMPTY;
        var graph = RegionRuntimeService.graph().orElse(null);
        if (graph == null) return ArrivalLocation.EMPTY;
        var plan = WorldPlanRuntime.generator(player.serverLevel()).map(g -> g.plan()).orElse(null);
        var p = player.blockPosition();
        return ArrivalPlaces.at(graph, plan, p.getX(), p.getZ());
    }

    private static void tick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick % ArrivalRules.INSTANCE.pollTicks() != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isRemoved() || player.isSpectator()) continue;
            var state = PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new ArrivalTracker(ArrivalRules.INSTANCE));
            state.observe(location(player), tick).ifPresent(update -> {
                PacketDistributor.sendToPlayer(player, new ArrivalPayload(player.level().dimension().location(),
                        update.location(), update.showRegion(), update.showTown()));
                LOG.info("ARRIVAL_NOTICE player={} region={} town={} showRegion={} showTown={}", player.getUUID(),
                        update.location().regionId(), update.location().townId(), update.showRegion(), update.showTown());
            });
        }
    }
}
