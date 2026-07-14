package com.example.myvillage.combat.session;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.network.CombatAttackStartPayload;
import com.example.myvillage.combat.network.CombatAttackStopPayload;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatDebugService;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatStepService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CombatSessionManager {
    private static final Map<UUID, CombatSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> BLOCKED_UNTIL_TICKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_INTENT_TICKS = new HashMap<>();
    private static final Map<UUID, StepRecord> STEP_SWEEPS = new HashMap<>();

    private CombatSessionManager() {
    }

    public static boolean handleAttackIntent(ServerPlayer player) {
        long tick = player.serverLevel().getGameTime();
        if (MeditationManager.status(player).state().active()) {
            MeditationManager.requestStop(player, MeditationStopReason.ATTACKED);
            interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
            sendRejection(player);
            return false;
        }
        CombatStopReason failure = eligibilityFailure(player, null);
        if (failure != null || !CombatTimingPolicy.recoveryComplete(
                tick, BLOCKED_UNTIL_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE))) {
            sendRejection(player);
            return false;
        }
        Long lastIntent = LAST_INTENT_TICKS.get(player.getUUID());
        if (!CombatTimingPolicy.allowsIntent(
                lastIntent, tick, BasicSwordStyle.DEFINITION.minimumIntentIntervalTicks())) {
            sendRejection(player);
            return false;
        }
        LAST_INTENT_TICKS.put(player.getUUID(), tick);

        CombatSession session = SESSIONS.computeIfAbsent(
                player.getUUID(), ignored -> new CombatSession(BasicSwordStyle.DEFINITION));
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        ResourceLocation worldId = player.level().dimension().location();
        CombatSession.IntentResult result = session.acceptIntent(
                tick, weaponId, worldId, player.yBodyRot);
        result.start().ifPresent(start -> broadcastStart(player, start));
        if (result.decision() == CombatSession.IntentDecision.REJECTED_TIMING
                || result.decision() == CombatSession.IntentDecision.REJECTED_BUFFER_FULL) {
            sendRejection(player);
            return false;
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (UUID playerId : SESSIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                removeRuntime(playerId, true);
                continue;
            }
            CombatSession session = SESSIONS.get(playerId);
            CombatStopReason failure = eligibilityFailure(player, session);
            if (failure != null) {
                interrupt(player, failure, preservesRecovery(failure));
                continue;
            }

            long tick = player.serverLevel().getGameTime();
            if (session.hasActiveAction()) {
                AttackMoveDefinition move = session.currentMove();
                int actionTick = session.actionTick(tick);
                move.step().filter(step -> step.actionTick() == actionTick).ifPresent(step -> {
                    Optional<CombatHitResolver.StepSweep> sweep = CombatStepService.tryStep(
                            player, step, session.facingYaw());
                    sweep.ifPresent(value -> STEP_SWEEPS.put(
                            playerId, new StepRecord(session.revision(), value)));
                });
                if (move.isActiveTick(actionTick)) {
                    Optional<CombatHitResolver.StepSweep> sweep = Optional.ofNullable(STEP_SWEEPS.get(playerId))
                            .filter(record -> record.revision() == session.revision())
                            .map(StepRecord::sweep);
                    CombatHitResolver.Resolution resolution = CombatHitResolver.resolve(
                            player, session, move, actionTick, sweep);
                    List<CombatHitResolver.TargetContact> successfulContacts = new ArrayList<>();
                    for (CombatHitResolver.TargetContact contact : resolution.contacts()) {
                        if (session.markAttempted(contact.target().getId())
                                && CombatDamageService.apply(
                                player, contact.target(), move, session).successful()) {
                            successfulContacts.add(contact);
                        }
                    }
                    CombatDebugService.render(player, resolution, successfulContacts);
                }
            }

            CombatSession.TickResult transition = session.tick(tick);
            transition.stop().ifPresent(stop -> broadcastStop(player, stop.revision(), stop.reason()));
            transition.start().ifPresent(start -> {
                STEP_SWEEPS.remove(playerId);
                broadcastStart(player, start);
            });
        }
        BLOCKED_UNTIL_TICKS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || player.serverLevel().getGameTime() >= entry.getValue();
        });
    }

    public static void interrupt(
            ServerPlayer player,
            CombatStopReason reason,
            boolean preserveRecovery) {
        CombatSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.interrupt(reason).ifPresent(stop -> {
            if (preserveRecovery) {
                BLOCKED_UNTIL_TICKS.merge(
                        player.getUUID(), stop.originalEndTick(), Math::max);
            }
            broadcastStop(player, stop.revision(), reason);
        });
        STEP_SWEEPS.remove(player.getUUID());
    }

    public static void removeRuntime(UUID playerId, boolean discardRecovery) {
        SESSIONS.remove(playerId);
        STEP_SWEEPS.remove(playerId);
        LAST_INTENT_TICKS.remove(playerId);
        if (discardRecovery) {
            BLOCKED_UNTIL_TICKS.remove(playerId);
        }
        CombatDebugService.clear(playerId);
    }

    public static long currentRevision(UUID playerId) {
        CombatSession session = SESSIONS.get(playerId);
        return session == null ? 0L : session.revision();
    }

    public static void clearAll(MinecraftServer server, CombatStopReason reason) {
        for (UUID playerId : SESSIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                interrupt(player, reason, false);
            }
        }
        SESSIONS.clear();
        BLOCKED_UNTIL_TICKS.clear();
        LAST_INTENT_TICKS.clear();
        STEP_SWEEPS.clear();
        CombatDebugService.clearAll();
    }

    private static CombatStopReason eligibilityFailure(
            ServerPlayer player,
            CombatSession session) {
        if (!player.isAlive() || player.isRemoved()) {
            return CombatStopReason.DEATH;
        }
        if (player.isSpectator() || player.isSleeping() || player.isUsingItem()) {
            return CombatStopReason.DISALLOWED;
        }
        if (player.isPassenger()) {
            return CombatStopReason.MOUNTED;
        }
        if (CombatService.getMode(player) != CombatMode.CULTIVATION) {
            return CombatStopReason.MODE_CHANGED;
        }
        if (!player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            return CombatStopReason.WEAPON_CHANGED;
        }
        if (MeditationManager.status(player).state().active()) {
            return CombatStopReason.CULTIVATION_STARTED;
        }
        if (session != null && session.hasActiveAction()
                && !player.level().dimension().location().equals(session.worldId())) {
            return CombatStopReason.DIMENSION_CHANGED;
        }
        return null;
    }

    private static boolean preservesRecovery(CombatStopReason reason) {
        return reason != CombatStopReason.DEATH
                && reason != CombatStopReason.LOGOUT
                && reason != CombatStopReason.DIMENSION_CHANGED
                && reason != CombatStopReason.SERVER_STOPPING;
    }

    private static void broadcastStart(ServerPlayer player, CombatSession.StartEvent start) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new CombatAttackStartPayload(
                        player.getId(), start.move().id(), start.startTick(), start.revision()));
    }

    private static void broadcastStop(
            ServerPlayer player,
            long revision,
            CombatStopReason reason) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new CombatAttackStopPayload(player.getId(), revision, reason));
    }

    private static void sendRejection(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new CombatAttackStopPayload(
                        player.getId(), currentRevision(player.getUUID()), CombatStopReason.REJECTED));
    }

    private record StepRecord(long revision, CombatHitResolver.StepSweep sweep) {
    }
}
