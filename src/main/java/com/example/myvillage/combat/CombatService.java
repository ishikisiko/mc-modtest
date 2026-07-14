package com.example.myvillage.combat;

import com.example.myvillage.combat.network.CombatModeSnapshotPayload;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CombatService {
    public static final int TOGGLE_INTERVAL_TICKS = 5;

    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE_TICKS = new HashMap<>();

    private CombatService() {
    }

    public static CombatPreference getPreference(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return player.getData(CombatAttachments.PREFERENCE.get());
    }

    public static CombatMode getMode(ServerPlayer player) {
        return getPreference(player).combatMode();
    }

    public static boolean toggleMode(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
            return false;
        }
        long tick = player.serverLevel().getGameTime();
        Long lastTick = LAST_TOGGLE_TICKS.get(player.getUUID());
        if (lastTick != null && tick - lastTick < TOGGLE_INTERVAL_TICKS) {
            return false;
        }
        LAST_TOGGLE_TICKS.put(player.getUUID(), tick);

        CombatPreference replacement = getPreference(player).toggled();
        player.setData(CombatAttachments.PREFERENCE.get(), replacement);
        long revision = incrementRevision(player.getUUID());
        if (replacement.combatMode() == CombatMode.VANILLA) {
            CombatSessionManager.interrupt(
                    player, CombatStopReason.MODE_CHANGED, true);
        }
        syncToClient(player, replacement, revision);
        player.displayClientMessage(Component.translatable(
                replacement.combatMode() == CombatMode.CULTIVATION
                        ? "message.myvillage.combat.mode.cultivation"
                        : "message.myvillage.combat.mode.vanilla"), true);
        return true;
    }

    public static void syncToClient(ServerPlayer player) {
        syncToClient(player, getPreference(player), revision(player.getUUID()));
    }

    public static long revision(UUID playerId) {
        return REVISIONS.getOrDefault(playerId, 0L);
    }

    public static void onPlayerRemoved(UUID playerId) {
        LAST_TOGGLE_TICKS.remove(playerId);
        REVISIONS.remove(playerId);
    }

    public static void clearRuntime() {
        LAST_TOGGLE_TICKS.clear();
        REVISIONS.clear();
    }

    private static void syncToClient(
            ServerPlayer player,
            CombatPreference preference,
            long revision) {
        PacketDistributor.sendToPlayer(
                player,
                new CombatModeSnapshotPayload(preference.combatMode(), revision));
    }

    private static long incrementRevision(UUID playerId) {
        long current = revision(playerId);
        long next = current == Long.MAX_VALUE ? 1L : current + 1L;
        REVISIONS.put(playerId, next);
        return next;
    }
}
