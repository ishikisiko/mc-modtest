package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CombatSession {
    private final CombatStyleDefinition style;
    private final Set<Integer> attemptedEntityIds = new HashSet<>();

    private int nextMoveIndex;
    private int currentMoveIndex = -1;
    private long actionStartTick;
    private long comboDeadline = Long.MIN_VALUE;
    private long revision;
    private boolean bufferedIntent;
    private boolean durabilityCharged;
    private boolean bookkeepingApplied;
    private ResourceLocation weaponId;
    private ResourceLocation worldId;
    private float facingYaw;
    private CombatStopReason lastStopReason = CombatStopReason.COMPLETED;

    public CombatSession(CombatStyleDefinition style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    public IntentResult acceptIntent(
            long serverTick,
            ResourceLocation currentWeaponId,
            ResourceLocation currentWorldId,
            float serverFacingYaw) {
        Objects.requireNonNull(currentWeaponId, "currentWeaponId");
        Objects.requireNonNull(currentWorldId, "currentWorldId");
        expireCombo(serverTick);
        if (hasActiveAction()) {
            int actionTick = actionTick(serverTick);
            if (!currentMove().acceptsBuffer(actionTick)) {
                return IntentResult.rejected(IntentDecision.REJECTED_TIMING);
            }
            if (bufferedIntent) {
                return IntentResult.rejected(IntentDecision.REJECTED_BUFFER_FULL);
            }
            bufferedIntent = true;
            return new IntentResult(IntentDecision.BUFFERED, Optional.empty());
        }
        return new IntentResult(
                IntentDecision.STARTED,
                Optional.of(start(nextMoveIndex, serverTick, currentWeaponId, currentWorldId, serverFacingYaw)));
    }

    public TickResult tick(long serverTick) {
        expireCombo(serverTick);
        if (!hasActiveAction() || actionTick(serverTick) < currentMove().totalTicks()) {
            return TickResult.none();
        }

        StopEvent stop = stopEvent(CombatStopReason.COMPLETED);
        int completedIndex = currentMoveIndex;
        boolean continueBuffered = bufferedIntent;
        clearAction();
        nextMoveIndex = completedIndex + 1;
        if (nextMoveIndex >= style.moves().size()) {
            nextMoveIndex = 0;
        }

        if (continueBuffered) {
            StartEvent start = start(
                    nextMoveIndex,
                    serverTick,
                    stop.weaponId(),
                    stop.worldId(),
                    stop.facingYaw());
            return new TickResult(Optional.of(stop), Optional.of(start));
        }

        comboDeadline = completedIndex == style.moves().size() - 1
                ? Long.MIN_VALUE
                : saturatedAdd(serverTick, style.comboTimeoutTicks());
        return new TickResult(Optional.of(stop), Optional.empty());
    }

    public Optional<StopEvent> interrupt(CombatStopReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!hasActiveAction()) {
            resetCombo(reason);
            return Optional.empty();
        }
        StopEvent stop = stopEvent(reason);
        clearAction();
        resetCombo(reason);
        return Optional.of(stop);
    }

    public void resetCombo(CombatStopReason reason) {
        nextMoveIndex = 0;
        comboDeadline = Long.MIN_VALUE;
        lastStopReason = Objects.requireNonNull(reason, "reason");
        bufferedIntent = false;
        attemptedEntityIds.clear();
    }

    public boolean markAttempted(int entityId) {
        return attemptedEntityIds.add(entityId);
    }

    public boolean wasAttempted(int entityId) {
        return attemptedEntityIds.contains(entityId);
    }

    public int attemptedTargetCount() {
        return attemptedEntityIds.size();
    }

    public int remainingTargetCapacity(int maximumTargets) {
        if (maximumTargets < 0) {
            throw new IllegalArgumentException("Maximum targets must be non-negative");
        }
        return Math.max(0, maximumTargets - attemptedEntityIds.size());
    }

    public boolean markDurabilityCharged() {
        if (durabilityCharged) {
            return false;
        }
        durabilityCharged = true;
        return true;
    }

    public boolean markBookkeepingApplied() {
        if (bookkeepingApplied) {
            return false;
        }
        bookkeepingApplied = true;
        return true;
    }

    public boolean hasActiveAction() {
        return currentMoveIndex >= 0;
    }

    public AttackMoveDefinition currentMove() {
        if (!hasActiveAction()) {
            throw new IllegalStateException("No active combat action");
        }
        return style.move(currentMoveIndex);
    }

    public int actionTick(long serverTick) {
        if (!hasActiveAction()) {
            return -1;
        }
        long elapsed = Math.max(0L, serverTick - actionStartTick);
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    public long actionEndTick() {
        return hasActiveAction()
                ? saturatedAdd(actionStartTick, currentMove().totalTicks())
                : Long.MIN_VALUE;
    }

    public long revision() {
        return revision;
    }

    public int nextMoveIndex() {
        return nextMoveIndex;
    }

    public long comboDeadline() {
        return comboDeadline;
    }

    public boolean bufferedIntent() {
        return bufferedIntent;
    }

    public ResourceLocation weaponId() {
        return weaponId;
    }

    public ResourceLocation worldId() {
        return worldId;
    }

    public float facingYaw() {
        return facingYaw;
    }

    public CombatStopReason lastStopReason() {
        return lastStopReason;
    }

    private StartEvent start(
            int moveIndex,
            long serverTick,
            ResourceLocation currentWeaponId,
            ResourceLocation currentWorldId,
            float serverFacingYaw) {
        currentMoveIndex = moveIndex;
        actionStartTick = serverTick;
        comboDeadline = Long.MIN_VALUE;
        bufferedIntent = false;
        attemptedEntityIds.clear();
        durabilityCharged = false;
        bookkeepingApplied = false;
        weaponId = currentWeaponId;
        worldId = currentWorldId;
        facingYaw = serverFacingYaw;
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        lastStopReason = CombatStopReason.COMPLETED;
        return new StartEvent(currentMove(), actionStartTick, revision, weaponId, worldId, facingYaw);
    }

    private StopEvent stopEvent(CombatStopReason reason) {
        lastStopReason = reason;
        return new StopEvent(
                currentMove(), revision, reason, actionEndTick(), weaponId, worldId, facingYaw);
    }

    private void clearAction() {
        currentMoveIndex = -1;
        bufferedIntent = false;
        attemptedEntityIds.clear();
        durabilityCharged = false;
        bookkeepingApplied = false;
    }

    private void expireCombo(long serverTick) {
        if (!hasActiveAction() && comboDeadline != Long.MIN_VALUE && serverTick > comboDeadline) {
            nextMoveIndex = 0;
            comboDeadline = Long.MIN_VALUE;
        }
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    public enum IntentDecision {
        STARTED,
        BUFFERED,
        REJECTED_TIMING,
        REJECTED_BUFFER_FULL
    }

    public record IntentResult(IntentDecision decision, Optional<StartEvent> start) {
        public IntentResult {
            Objects.requireNonNull(decision, "decision");
            start = Objects.requireNonNull(start, "start");
        }

        private static IntentResult rejected(IntentDecision decision) {
            return new IntentResult(decision, Optional.empty());
        }
    }

    public record StartEvent(
            AttackMoveDefinition move,
            long startTick,
            long revision,
            ResourceLocation weaponId,
            ResourceLocation worldId,
            float facingYaw) {
    }

    public record StopEvent(
            AttackMoveDefinition move,
            long revision,
            CombatStopReason reason,
            long originalEndTick,
            ResourceLocation weaponId,
            ResourceLocation worldId,
            float facingYaw) {
    }

    public record TickResult(Optional<StopEvent> stop, Optional<StartEvent> start) {
        public TickResult {
            stop = Objects.requireNonNull(stop, "stop");
            start = Objects.requireNonNull(start, "start");
        }

        private static TickResult none() {
            return new TickResult(Optional.empty(), Optional.empty());
        }
    }
}
