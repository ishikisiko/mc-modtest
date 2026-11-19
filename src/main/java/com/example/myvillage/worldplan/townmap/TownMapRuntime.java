package com.example.myvillage.worldplan.townmap;

import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Supplies the placement already chosen by worldgen. The map never loads chunks. */
public final class TownMapRuntime {
    private static final Map<UUID, TownMapLocation> LAST_SENT = new HashMap<>();

    private TownMapRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TownMapRuntime::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) update(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) update(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> LAST_SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> LAST_SENT.clear());
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) update(player);
    }

    private static void update(ServerPlayer player) {
        var location = WorldPlanRuntime.generator(player.serverLevel())
                .filter(generator -> player.serverLevel().getServer().getWorldData().worldGenOptions().generateStructures())
                .flatMap(generator -> generator.plan().cities().stream()
                        .filter(city -> city.contains(player.getBlockX(), player.getBlockZ(), city.margin()))
                        .min(Comparator.comparingDouble((CapitalCity city) -> city.distance(player.getX(), player.getZ()))
                                .thenComparing(CapitalCity::cityId)))
                .map(city -> new TownMapLocation(city.cityId(), city.bundle().bundleHash,
                        new BlockPos(city.x(), city.originY(), city.z())))
                .orElse(TownMapLocation.EMPTY);
        if (!location.equals(LAST_SENT.get(player.getUUID()))) {
            PacketDistributor.sendToPlayer(player, location);
            LAST_SENT.put(player.getUUID(), location);
        }
    }
}
