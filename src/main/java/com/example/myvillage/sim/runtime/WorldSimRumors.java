package com.example.myvillage.sim.runtime;

import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 江湖传闻: after each settled day, major ledger events (importance 3) are told to every online
 * player, and notable ones (importance 2) to the players standing in the event's region, as chat
 * lines rendered through the event's language key. Players in the event's region hear first. Each
 * player hears at most {@code rumors_per_minute} per real minute; the rest wait in a capped queue.
 * Off with {@code rumors_enabled = false}.
 */
public final class WorldSimRumors {
    public static final String RUMOR_KEY = "message.myvillage.world.rumor";
    private static final int DRAIN_INTERVAL_TICKS = 20;
    private static final RumorBoard<UUID, Component> BOARD = new RumorBoard<>();

    private WorldSimRumors() {
    }

    static void register() {
        WorldSimRuntime.addListener(WorldSimRumors::onDaySettled);
        NeoForge.EVENT_BUS.addListener(WorldSimRumors::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorldSimRumors::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(WorldSimRumors::onServerStopping);
    }

    /** The chat line for one event. */
    public static Component message(SimEvent event) {
        return Component.translatable(RUMOR_KEY, WorldSimText.event(event).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GOLD);
    }

    static void onDaySettled(MinecraftServer server, WorldSim sim, List<SimEvent> events) {
        if (!WorldSimServerConfig.rumorsEnabled()) {
            BOARD.clear();
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        int cap = WorldSimServerConfig.rumorQueueCap();
        for (SimEvent event : events) {
            if (event.importance() < RumorBoard.NOTABLE) {
                continue;
            }
            List<ServerPlayer> audience = RumorBoard.audience(event, players, RegionRuntimeService::currentRegion);
            if (audience.isEmpty()) {
                continue;
            }
            Component line = message(event);
            for (ServerPlayer player : audience) {
                BOARD.offer(player.getUUID(), line, cap);
            }
        }
        drain(server);
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % DRAIN_INTERVAL_TICKS == 0) {
            drain(event.getServer());
        }
    }

    private static void drain(MinecraftServer server) {
        for (RumorBoard.Entry<UUID, Component> entry
                : BOARD.due(System.currentTimeMillis(), WorldSimServerConfig.rumorsPerMinute())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.player());
            if (player != null) {
                player.sendSystemMessage(entry.message());
            }
        }
    }

    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BOARD.forget(event.getEntity().getUUID());
    }

    static void onServerStopping(ServerStoppingEvent event) {
        BOARD.clear();
    }
}
