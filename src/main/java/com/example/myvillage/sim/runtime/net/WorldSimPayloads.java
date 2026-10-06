package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.client.sim.ClientWorldSimState;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.runtime.WorldSimDriver;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.player.ScriptureHall;
import com.example.myvillage.sim.runtime.player.SectDialogue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Networking of the 天下 page: the serverbound {@link WorldSimQueryPayload} and the clientbound
 * {@link WorldSimSnapshotPayload}. The server answers every query with a read-only snapshot
 * ({@link WorldSimSnapshots}); a player's query that arrives less than {@link #MIN_TICKS_BETWEEN}
 * server ticks after their previous one is dropped without an answer.
 */
public final class WorldSimPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSimPayloads.class);
    /** Least server ticks between two answered queries of one player. */
    static final int MIN_TICKS_BETWEEN = 4;
    /** Players remembered for throttling; the oldest is forgotten beyond this (no logout hook needed). */
    private static final int MAX_TRACKED_PLAYERS = 256;

    /** Last answered tick per player; touched only on the server thread. */
    private static final Map<UUID, Integer> LAST_QUERY_TICK = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_TRACKED_PLAYERS;
        }
    };

    private WorldSimPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                WorldSimQueryPayload.TYPE,
                WorldSimQueryPayload.STREAM_CODEC,
                WorldSimPayloads::handleQuery);
        registrar.playToClient(
                WorldSimSnapshotPayload.TYPE,
                WorldSimSnapshotPayload.STREAM_CODEC,
                WorldSimPayloads::handleSnapshot);
        registrar.playToClient(
                SectDialoguePayload.TYPE,
                SectDialoguePayload.STREAM_CODEC,
                WorldSimPayloads::handleSectDialogue);
        registrar.playToServer(
                SectIntentPayload.TYPE,
                SectIntentPayload.STREAM_CODEC,
                WorldSimPayloads::handleSectIntent);
        registrar.playToClient(
                ScriptureHallPayload.TYPE,
                ScriptureHallPayload.STREAM_CODEC,
                WorldSimPayloads::handleScriptureHall);
        registrar.playToServer(
                ScriptureBorrowPayload.TYPE,
                ScriptureBorrowPayload.STREAM_CODEC,
                WorldSimPayloads::handleScriptureBorrow);
        // ClientWorldSimState holds no client-only types; on a dedicated server the sender is never invoked.
        ClientWorldSimState.installSender(query -> PacketDistributor.sendToServer(new WorldSimQueryPayload(query)));
    }

    private static void handleSectDialogue(SectDialoguePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SectDialoguePayload.receive(payload));
    }

    private static void handleSectIntent(SectIntentPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> SectDialogue.handleIntent(player, payload));
    }

    private static void handleScriptureHall(ScriptureHallPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ScriptureHallPayload.receive(payload));
    }

    private static void handleScriptureBorrow(ScriptureBorrowPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> ScriptureHall.handleBorrow(player, payload));
    }

    private static void handleQuery(WorldSimQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            MinecraftServer server = player.getServer();
            if (server == null || !allow(player.getUUID(), server.getTickCount())) {
                return;
            }
            WorldSimSnapshot snapshot;
            try {
                snapshot = answer(server, player, payload.query());
            } catch (RuntimeException ex) {
                LOGGER.warn("World-sim query {} from {} failed", payload.query(), player.getGameProfile().getName(), ex);
                return;
            }
            PacketDistributor.sendToPlayer(player, new WorldSimSnapshotPayload(snapshot));
        });
    }

    private static void handleSnapshot(WorldSimSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientWorldSimState.receive(payload.snapshot()));
    }

    /** True (and remembered) unless the player's previous query was under {@link #MIN_TICKS_BETWEEN} ticks ago. */
    static boolean allow(UUID player, int tick) {
        Integer last = LAST_QUERY_TICK.get(player);
        // a tick before the last one means a new server in this JVM: start over
        if (last != null && tick >= last && tick - last < MIN_TICKS_BETWEEN) {
            return false;
        }
        LAST_QUERY_TICK.put(player, tick);
        return true;
    }

    private static WorldSimSnapshot answer(MinecraftServer server, ServerPlayer player, WorldSimQuery query) {
        Optional<WorldSimDriver> active = WorldSimRuntime.driver();
        if (active.isEmpty()) {
            return WorldSimSnapshots.inactive(query, WorldSimRuntime.inactiveReason());
        }
        WorldSimDriver driver = active.get();
        Optional<String> here = query.kind() == WorldSimQuery.Kind.HERE
                ? RegionRuntimeService.currentRegion(player)
                : Optional.empty();
        return WorldSimSnapshots.build(driver.sim(), WorldSimRuntime.daysPerYear(),
                WorldSimRuntime.calendarDay(server), driver.paused(), driver.pendingDays(),
                WorldSimRuntime::regionName, here, player.getX(), player.getZ(), player.getUUID().toString(), query);
    }
}
