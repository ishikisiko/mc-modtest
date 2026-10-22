package com.example.myvillage.combat.session;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatAttackStartPayload;
import com.example.myvillage.combat.network.CombatAttackStopPayload;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.runtime.CombatDebugService;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatFeedbackService;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatStepService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.combat.runtime.CombatReactionService;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    /** Transient MOVEMENT_SPEED modifier that commits the body to an action. */
    public static final ResourceLocation COMMIT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_commit");
    /** Anticipation and strike (start through activeEnd): movement x0.25. */
    static final double STRIKE_COMMITMENT = -0.75;
    /** Recovery until chainTick (or the end of a move that cannot chain): movement x0.6. */
    static final double RECOVERY_COMMITMENT = -0.4;

    private CombatSessionManager() {
    }

    public static boolean handleAttackIntent(ServerPlayer player) {
        if (CombatSkillActions.has(player) || CombatArtActions.has(player)) {
            sendRejection(player);
            return false;
        }
        // No attack inside a dodge's invulnerable window; once it closes, dodge-into-attack is fine.
        if (CombatDodgeService.isDodging(player)) {
            sendRejection(player);
            return false;
        }
        long tick = player.serverLevel().getGameTime();
        if (MeditationManager.status(player).state().active()) {
            MeditationManager.requestStop(player, MeditationStopReason.ATTACKED);
            interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
            sendRejection(player);
            return false;
        }
        CombatStopReason failure = eligibilityFailure(player, null);
        Optional<CombatStyleDefinition> style = CombatStyles.bundled().styleFor(player.getMainHandItem());
        if (failure == null) {
            failure = weaponFailure(style, SESSIONS.get(player.getUUID()));
        }
        if (failure != null || style.isEmpty() || !CombatTimingPolicy.recoveryComplete(
                tick, BLOCKED_UNTIL_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE))) {
            sendRejection(player);
            return false;
        }
        Long lastIntent = LAST_INTENT_TICKS.get(player.getUUID());
        if (!CombatTimingPolicy.allowsIntent(
                lastIntent, tick, style.get().minimumIntentIntervalTicks())) {
            sendRejection(player);
            return false;
        }
        return acceptBasic(player, style.get(), tick);
    }

    public static boolean handleAttackIntent(NpcEntity npc) {
        if (!NpcCombatRuntime.canAct(npc) || CombatReactionService.isStaggered(npc)) return false;
        var style = CombatStyles.bundled().styleFor(npc.getMainHandItem());
        long tick = npc.level().getGameTime();
        if (style.isEmpty() || !CombatTimingPolicy.recoveryComplete(tick,
                BLOCKED_UNTIL_TICKS.getOrDefault(npc.getUUID(), Long.MIN_VALUE))
                || !CombatTimingPolicy.allowsIntent(LAST_INTENT_TICKS.get(npc.getUUID()), tick,
                    style.get().minimumIntentIntervalTicks())) return false;
        return acceptBasic(npc, style.get(), tick);
    }

    private static boolean acceptBasic(LivingEntity actor, CombatStyleDefinition style, long tick) {
        CombatSession session = SESSIONS.computeIfAbsent(
                actor.getUUID(), ignored -> new CombatSession(style));
        // Between actions the session follows the held weapon; during one, eligibility has already
        // rejected a weapon of another style.
        session.useStyle(style);
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(actor.getMainHandItem().getItem());
        ResourceLocation worldId = actor.level().dimension().location();
        CombatSession.IntentResult result = session.acceptIntent(
                tick, weaponId, worldId, actor.getYRot());
        if (result.decision() == CombatSession.IntentDecision.REJECTED_TIMING
                || result.decision() == CombatSession.IntentDecision.REJECTED_BUFFER_FULL) {
            if (actor instanceof ServerPlayer player) sendRejection(player);
            return false;
        }
        // Only accepted or buffered intents count toward the minimum interval, so a click the
        // session rejected never blocks the next, valid click.
        LAST_INTENT_TICKS.put(actor.getUUID(), tick);
        result.start().ifPresent(start -> beginAction(actor, start));
        return true;
    }

    /** Two bounded advanced-art slots share the same authoritative action ownership. */
    public static boolean handleArtIntent(ServerPlayer player, int slot) {
        if (!CombatArtActions.intentDue(player, slot)
                || eligibilityFailure(player, null) != null || CombatDodgeService.isDodging(player)) return false;
        long tick = player.serverLevel().getGameTime();
        CombatSession session = SESSIONS.get(player.getUUID());
        if (CombatSkillActions.has(player) || CombatArtActions.has(player)
                || (session != null && session.hasActiveAction())
                || !CombatTimingPolicy.recoveryComplete(tick,
                    BLOCKED_UNTIL_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE))) return false;
        CombatStyleDefinition style = CombatStyles.bundled().styleFor(player.getMainHandItem()).orElseThrow();
        if (!CombatTimingPolicy.allowsIntent(LAST_INTENT_TICKS.get(player.getUUID()), tick,
                style.minimumIntentIntervalTicks())) return false;
        session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new CombatSession(style));
        session.useStyle(style);
        Optional<CombatSession.StartEvent> start = CombatArtActions.start(player, session, slot);
        if (start.isEmpty()) return false;
        LAST_INTENT_TICKS.put(player.getUUID(), tick);
        beginAction(player, start.get());
        return true;
    }

    public static boolean handleChargeIntent(ServerPlayer player) {
        long tick = player.serverLevel().getGameTime();
        if (eligibilityFailure(player, null) != null || CombatDodgeService.isDodging(player)) {
            return CombatSkillActions.reject(player, "STATE");
        }
        CombatSession session = SESSIONS.get(player.getUUID());
        if (CombatSkillActions.has(player) || CombatArtActions.has(player) || (session != null && session.hasActiveAction())
                || !CombatTimingPolicy.recoveryComplete(tick,
                BLOCKED_UNTIL_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE))) {
            return CombatSkillActions.reject(player, "BUSY");
        }
        CombatStyleDefinition style = CombatStyles.bundled().styleFor(player.getMainHandItem()).orElseThrow();
        Long last = LAST_INTENT_TICKS.get(player.getUUID());
        if (!CombatTimingPolicy.allowsIntent(last, tick, style.minimumIntentIntervalTicks())) {
            return CombatSkillActions.reject(player, "BUSY");
        }
        session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new CombatSession(style));
        session.useStyle(style);
        boolean accepted = CombatSkillActions.start(player, session);
        if (accepted) LAST_INTENT_TICKS.put(player.getUUID(), tick);
        return accepted;
    }

    public static boolean handleReleaseIntent(ServerPlayer player) {
        CombatSession session = SESSIONS.get(player.getUUID());
        if (session == null || !CombatSkillActions.charging(player)) return false;
        if (eligibilityFailure(player, session) != null || !CombatSkillActions.valid(player)
                || CombatDodgeService.isDodging(player)) {
            interrupt(player, CombatStopReason.CHARGE_CANCELLED, false);
            return false;
        }
        Optional<CombatSession.StartEvent> start = CombatSkillActions.release(player, session);
        start.ifPresent(value -> beginAction(player, value));
        return start.isPresent();
    }

    /** Menu/focus cancellation is input intent only: it cannot cancel or refund a released action. */
    public static boolean handleChargeCancelIntent(ServerPlayer player) {
        if (!CombatSkillActions.charging(player)) return false;
        interrupt(player, CombatStopReason.CHARGE_CANCELLED, false);
        return true;
    }

    /** Positive resolved damage interrupts charge/action without refunding an already released skill. */
    public static void onSkillDamage(ServerPlayer player) { onSkillDamage(player, null); }

    public static void onSkillDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        if (CombatSkillActions.has(player)
                || (CombatArtActions.has(player) && CombatArtActions.damageInterrupts(player, source)))
            interrupt(player, CombatStopReason.CHARGE_CANCELLED, true);
    }

    public static void tick(MinecraftServer server) {
        NpcCombatRuntime.tick(server);
        com.example.myvillage.cultivation.fate.SwordObsessionRuntime.tick(server);
        com.example.myvillage.combat.runtime.DerivedSwordWaves.tick(server);
        for (UUID playerId : SESSIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                NpcEntity npc = NpcCombatRuntime.find(playerId);
                if (npc != null) tickNpc(npc);
                else removeRuntime(playerId, true);
                continue;
            }
            CombatSession session = SESSIONS.get(playerId);
            CombatStopReason failure = eligibilityFailure(player, session);
            if (failure == null && (!CombatSkillActions.valid(player) || !CombatArtActions.valid(player)))
                failure = CombatStopReason.WEAPON_CHANGED;
            if (failure != null) {
                interrupt(player, failure, preservesRecovery(failure));
                continue;
            }

            CombatSkillActions.tickCharge(player);
            long tick = player.serverLevel().getGameTime();
            CombatSession.TickResult transition = advanceAction(player, session, tick);
            transition.stop().ifPresent(stop -> broadcastStop(player, stop.revision(), stop.reason()));
            if (transition.start().isPresent()) {
                STEP_SWEEPS.remove(playerId);
                beginAction(player, transition.start().orElseThrow());
            } else if (transition.stop().isPresent()) {
                CombatSkillActions.stop(player, "COMPLETED");
                STEP_SWEEPS.remove(playerId);
                Optional<CombatSession.StartEvent> nextArtPhase = CombatArtActions.advance(player);
                if (nextArtPhase.isPresent()) beginAction(player, nextArtPhase.get());
                else clearCommitment(player);
            }
        }
        CombatArtActions.prune(server.overworld().getGameTime());
        BLOCKED_UNTIL_TICKS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            NpcEntity npc = NpcCombatRuntime.find(entry.getKey());
            return player != null ? player.level().getGameTime() >= entry.getValue()
                    : npc == null || npc.level().getGameTime() >= entry.getValue();
        });
    }

    /** One executor for basic attacks from input packets and AI intents. */
    private static CombatSession.TickResult advanceAction(LivingEntity actor, CombatSession session, long tick) {
            if (session.hasActiveAction()) {
                AttackMoveDefinition move = session.currentMove();
                int actionTick = session.actionTick(tick);
                updateCommitment(actor, move, actionTick);
                move.step().filter(step -> step.actionTick() == actionTick).ifPresent(step -> {
                    Optional<CombatHitResolver.StepSweep> sweep = CombatStepService.tryStep(
                            actor, move, step, session.facingYaw(), session);
                    sweep.ifPresent(value -> STEP_SWEEPS.put(
                            actor.getUUID(), new StepRecord(session.revision(), value)));
                });
                // The whoosh leads the blade by one tick so it lands with the visible strike.
                if (actionTick == move.activeStartTick() - 1) {
                    CombatFeedbackService.swing(actor, move);
                }
                if (session.isExternal() && actor instanceof ServerPlayer player) {
                    if (CombatArtActions.has(player)) {
                        Optional<CombatHitResolver.StepSweep> sweep = Optional.ofNullable(STEP_SWEEPS.get(actor.getUUID()))
                                .filter(record -> record.revision() == session.revision()).map(StepRecord::sweep);
                        CombatArtActions.resolve(player, session, sweep);
                    } else {
                        CombatSkillActions.resolve(player, session);
                    }
                } else if (move.isActiveTick(actionTick)) {
                    Optional<CombatHitResolver.StepSweep> sweep = Optional.ofNullable(STEP_SWEEPS.get(actor.getUUID()))
                            .filter(record -> record.revision() == session.revision())
                            .map(StepRecord::sweep);
                    CombatHitResolver.Resolution resolution = CombatHitResolver.resolve(
                            actor, session, move, actionTick, sweep);
                    List<CombatHitResolver.TargetContact> successfulContacts = new ArrayList<>();
                    for (CombatHitResolver.TargetContact contact : resolution.contacts()) {
                        if (session.markAttempted(CombatTargets.hitId(contact.target()))
                                && CombatDamageService.apply(
                                actor, contact.target(), move, session).successful()) {
                            successfulContacts.add(contact);
                        }
                    }
                    CombatFeedbackService.hit(actor, move, session.revision(), successfulContacts);
                    if (actor instanceof ServerPlayer player) CombatDebugService.render(player, resolution, successfulContacts);
                }
            }

            return session.tick(tick, actor.getYRot());
    }

    private static void tickNpc(NpcEntity npc) {
        if (!NpcCombatRuntime.canTick(npc)) return;
        CombatSession session = SESSIONS.get(npc.getUUID());
        long tick = npc.level().getGameTime();
        if (!NpcCombatRuntime.canAct(npc) || weaponFailure(CombatStyles.bundled().styleFor(npc.getMainHandItem()), session) != null) {
            interruptNpc(npc, CombatStopReason.DISALLOWED, true); return;
        }
        if (CombatReactionService.isStunned(npc)) {
            interruptNpc(npc, CombatStopReason.DISALLOWED, true); return;
        }
        if (CombatReactionService.isFrozen(npc)) {
            session.pauseOneTick();
            npc.syncCombat(session, tick, true);
            return;
        }
        if (session.hasActiveAction()) {
            npc.getNavigation().stop();
            npc.setYRot(session.facingYaw());
            npc.setYBodyRot(session.facingYaw());
            npc.setYHeadRot(session.facingYaw());
        }
        CombatSession.TickResult transition = advanceAction(npc, session, tick);
        transition.stop().ifPresent(stop -> broadcastStop(npc, stop.revision(), stop.reason()));
        if (transition.start().isPresent()) {
            STEP_SWEEPS.remove(npc.getUUID());
            beginAction(npc, transition.start().get());
        } else if (transition.stop().isPresent()) {
            STEP_SWEEPS.remove(npc.getUUID()); clearCommitment(npc);
        }
        npc.syncCombat(session, tick, false);
        NpcCombatRuntime.checkpoint(npc);
    }

    public static boolean hasAction(NpcEntity npc) {
        CombatSession s = SESSIONS.get(npc.getUUID()); return s != null && s.hasActiveAction();
    }

    public static long recoveryUntil(NpcEntity npc) {
        CombatSession s = SESSIONS.get(npc.getUUID());
        return Math.max(0, Math.max(BLOCKED_UNTIL_TICKS.getOrDefault(npc.getUUID(), 0L),
                s == null ? 0 : s.actionEndTick()));
    }

    public static void restoreRecovery(NpcEntity npc, long until) {
        BLOCKED_UNTIL_TICKS.put(npc.getUUID(), until);
    }

    public static void interruptNpc(NpcEntity npc, CombatStopReason reason, boolean preserveRecovery) {
        CombatSession session = SESSIONS.get(npc.getUUID());
        if (session != null) {
            if (preserveRecovery && session.hasActiveAction())
                BLOCKED_UNTIL_TICKS.merge(npc.getUUID(), session.actionEndTick(), Math::max);
            session.interrupt(reason).ifPresent(stop -> broadcastStop(npc, stop.revision(), stop.reason()));
        }
        STEP_SWEEPS.remove(npc.getUUID());
        clearCommitment(npc);
        npc.clearCombatAction();
    }

    /**
     * Whether a dodge may start now as far as the attack session is concerned: true with no action
     * running, or once the action is past its last active tick (its recovery). Has no side effect.
     */
    public static boolean canDodgeCancel(ServerPlayer player) {
        return dodgeCancelAllowed(SESSIONS.get(player.getUUID()), player.serverLevel().getGameTime());
    }

    /**
     * Makes room for a dodge: true with no action running; in an action's recovery
     * ({@code actionTick > activeEndTick}) the action is interrupted with {@link CombatStopReason#DODGED}
     * (combo reset, no recovery block) and true is returned; during anticipation or the strike the
     * dodge is refused (false) and nothing changes.
     */
    public static boolean tryDodgeCancel(ServerPlayer player) {
        CombatSession session = SESSIONS.get(player.getUUID());
        long tick = player.serverLevel().getGameTime();
        if (!dodgeCancelAllowed(session, tick)) {
            return false;
        }
        if (CombatSkillActions.has(player) || CombatArtActions.has(player) || (session != null && session.hasActiveAction())) {
            interrupt(player, CombatStopReason.DODGED, false);
        }
        return true;
    }

    /** The recovery-only dodge-cancel rule: no action, or {@code actionTick > move.activeEndTick()}. */
    static boolean dodgeCancelAllowed(CombatSession session, long serverTick) {
        if (session == null || !session.hasActiveAction()) {
            return true;
        }
        return session.actionTick(serverTick) > session.currentMove().activeEndTick();
    }

    public static void interrupt(
            ServerPlayer player,
            CombatStopReason reason,
            boolean preserveRecovery) {
        boolean cancelledCharge = CombatSkillActions.charging(player);
        CombatArtActions.stop(player, reason.name());
        CombatSkillActions.stop(player, reason.name());
        CombatSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            clearCommitment(player);
            return;
        }
        if (cancelledCharge) session.discardPendingAction();
        session.interrupt(reason).ifPresent(stop -> {
            if (preserveRecovery) {
                BLOCKED_UNTIL_TICKS.merge(
                        player.getUUID(), stop.originalEndTick(), Math::max);
            }
            broadcastStop(player, stop.revision(), reason);
        });
        STEP_SWEEPS.remove(player.getUUID());
        clearCommitment(player);
    }

    /**
     * Removes the action movement commitment. Safe to call at any time; every stop path
     * (completion without a chain, interrupt, death, logout, dimension change, respawn) calls it.
     */
    public static void clearCommitment(LivingEntity player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(COMMIT_MODIFIER_ID);
        }
    }

    /** Commitment for one action tick: strike weight until activeEnd, lighter until chainTick. */
    static double commitmentAt(AttackMoveDefinition move, int actionTick) {
        if (actionTick <= move.activeEndTick()) {
            return STRIKE_COMMITMENT;
        }
        return move.chainsAt(actionTick) ? 0.0 : RECOVERY_COMMITMENT;
    }

    private static void updateCommitment(LivingEntity player, AttackMoveDefinition move, int actionTick) {
        double amount = commitmentAt(move, actionTick);
        if (amount == 0.0) {
            clearCommitment(player);
            return;
        }
        setCommitment(player, amount);
    }

    static void setCommitment(LivingEntity player, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(COMMIT_MODIFIER_ID);
        if (current != null && current.amount() == amount) {
            return;
        }
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                COMMIT_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * Server side of a started action: the body and head snap to the view yaw the action faces,
     * sprinting ends (as vanilla melee does), and movement is committed.
     */
    private static void beginAction(LivingEntity player, CombatSession.StartEvent start) {
        player.setYBodyRot(start.facingYaw());
        player.setYHeadRot(start.facingYaw());
        player.setSprinting(false);
        setCommitment(player, STRIKE_COMMITMENT);
        if (player instanceof NpcEntity npc) {
            npc.syncCombat(SESSIONS.get(npc.getUUID()), npc.level().getGameTime(), false);
            NpcCombatRuntime.checkpoint(npc);
        }
        broadcastStart(player, start);
    }

    public static void removeRuntime(UUID playerId, boolean discardRecovery) {
        com.example.myvillage.combat.runtime.CombatDisciplines.forget(playerId);
        com.example.myvillage.combat.runtime.CombatHotbarService.forget(playerId);
        CombatSkillActions.remove(playerId);
        CombatArtActions.remove(playerId);
        SESSIONS.remove(playerId);
        STEP_SWEEPS.remove(playerId);
        LAST_INTENT_TICKS.remove(playerId);
        if (discardRecovery) {
            BLOCKED_UNTIL_TICKS.remove(playerId);
        }
        CombatDebugService.clear(playerId);
    }

    public static boolean hudBusy(ServerPlayer player) {
        var session=SESSIONS.get(player.getUUID());
        return CombatSkillActions.has(player) || CombatArtActions.has(player)
                || (session!=null && session.hasActiveAction())
                || player.serverLevel().getGameTime()<BLOCKED_UNTIL_TICKS.getOrDefault(player.getUUID(),Long.MIN_VALUE);
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
        com.example.myvillage.combat.runtime.CombatDisciplines.clear();
        com.example.myvillage.combat.runtime.CombatHotbarService.clear();
        CombatSkillActions.clear();
        CombatArtActions.clear();
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
        CombatStopReason weaponFailure = weaponFailure(
                CombatStyles.bundled().styleFor(player.getMainHandItem()), session);
        if (weaponFailure != null) {
            return weaponFailure;
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

    /**
     * WEAPON_CHANGED when the held item is not a registered weapon, or when an action is running
     * with another style than the held weapon's. Two weapons of one style are interchangeable.
     */
    static CombatStopReason weaponFailure(Optional<CombatStyleDefinition> heldStyle, CombatSession session) {
        if (heldStyle.isEmpty()) {
            return CombatStopReason.WEAPON_CHANGED;
        }
        if (session != null && session.hasActiveAction() && !session.style().equals(heldStyle.get())) {
            return CombatStopReason.WEAPON_CHANGED;
        }
        return null;
    }

    private static boolean preservesRecovery(CombatStopReason reason) {
        return reason != CombatStopReason.DEATH
                && reason != CombatStopReason.LOGOUT
                && reason != CombatStopReason.DIMENSION_CHANGED
                && reason != CombatStopReason.SERVER_STOPPING;
    }

    private static void broadcastStart(LivingEntity player, CombatSession.StartEvent start) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new CombatAttackStartPayload(
                        player.getId(),
                        start.move().id(),
                        start.startTick(),
                        start.revision(),
                        start.facingYaw()));
    }

    private static void broadcastStop(
            LivingEntity player,
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
