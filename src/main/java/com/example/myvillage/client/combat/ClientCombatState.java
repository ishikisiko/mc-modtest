package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;

import java.util.HashMap;
import java.util.Map;

final class ClientCombatState {
    private static final Map<Integer, Long> ACTION_REVISIONS = new HashMap<>();

    private static CombatMode mode = CombatMode.VANILLA;
    private static long preferenceRevision = -1L;
    private static int predictedNextMoveIndex;
    private static boolean predictionPending;
    private static long predictionTick;
    private static long lastCompletedActionTick = Long.MIN_VALUE;
    private static boolean readyAnimation;
    private static boolean localActionActive;
    /**
     * The local player's current (confirmed or predicted) action timeline. A predicted action
     * has revision -1 until the server's START confirms it.
     */
    private static int localMoveIndex = -1;
    private static long localStartTick;
    private static long localRevision = -1L;
    private static int localCueTick = -1;
    /** One click held inside the current move's buffer window (the server's one-slot buffer). */
    private static boolean bufferedClick;
    /** The pending prediction is a chained move started locally at the previous move's chainTick. */
    private static boolean chainPrediction;
    private static long chainSourceRevision = -1L;
    private static long chainSourceStoppedTick = Long.MIN_VALUE;

    private ClientCombatState() {
    }

    static boolean replaceMode(CombatMode replacement, long revision) {
        if (revision < preferenceRevision) {
            return false;
        }
        boolean changed = mode != replacement;
        mode = replacement;
        preferenceRevision = revision;
        ACTION_REVISIONS.clear();
        if (changed) {
            predictedNextMoveIndex = 0;
            predictionPending = false;
            lastCompletedActionTick = Long.MIN_VALUE;
            readyAnimation = false;
            localActionActive = false;
            clearLocalAction();
        }
        return changed;
    }

    static boolean acceptActionRevision(int entityId, long revision) {
        long previous = ACTION_REVISIONS.getOrDefault(entityId, -1L);
        if (revision < previous) {
            return false;
        }
        ACTION_REVISIONS.put(entityId, revision);
        return true;
    }

    static void resetActionRevision(int entityId) {
        ACTION_REVISIONS.remove(entityId);
    }

    static void beginPrediction(long tick) {
        predictionPending = true;
        predictionTick = tick;
        readyAnimation = false;
    }

    static void confirmPrediction(int nextMoveIndex) {
        predictedNextMoveIndex = nextMoveIndex;
        predictionPending = false;
        readyAnimation = false;
        localActionActive = true;
        chainPrediction = false;
        chainSourceRevision = -1L;
        chainSourceStoppedTick = Long.MIN_VALUE;
    }

    /**
     * Records the local player's action timeline: from a local prediction (revision -1) or from
     * the server's START. Confirming the move already predicted keeps its cue progress, so the
     * swing lean and lunge surge never play twice.
     */
    static void trackLocalAction(int moveIndex, long startTick, long revision) {
        boolean samePredictedMove = moveIndex == localMoveIndex && localRevision < 0L && revision >= 0L;
        if (!samePredictedMove) {
            localCueTick = -1;
        }
        localMoveIndex = moveIndex;
        localStartTick = startTick;
        localRevision = revision;
        bufferedClick = false;
    }

    /**
     * Holds a click made during the local action when the current move would buffer it, like
     * the server session: from {@code bufferStartTick} to the end of the move, one slot.
     */
    static boolean bufferClick(long tick, CombatStyleDefinition style) {
        if (localMoveIndex < 0 || bufferedClick || (!localActionActive && !predictionPending)) {
            return false;
        }
        AttackMoveDefinition move = style.move(localMoveIndex);
        int actionTick = actionTick(tick);
        if (!move.acceptsBuffer(actionTick) && actionTick < move.totalTicks()) {
            return false;
        }
        bufferedClick = true;
        return true;
    }

    /**
     * The move to predict now, or -1: a confirmed local action with a held click that reached
     * its chain tick. Mirrors the server, which starts the next move at {@code chainTick} when
     * it holds a buffered intent.
     */
    static int chainDue(long tick, CombatStyleDefinition style) {
        if (!bufferedClick || !localActionActive || predictionPending || localMoveIndex < 0) {
            return -1;
        }
        AttackMoveDefinition move = style.move(localMoveIndex);
        if (!move.chainsAt(actionTick(tick))) {
            return -1;
        }
        return (localMoveIndex + 1) % style.moves().size();
    }

    /** Starts the locally predicted chained move; the server's START for it confirms it later. */
    static void beginChainPrediction(long tick, int nextMoveIndex) {
        chainSourceRevision = localRevision;
        chainSourceStoppedTick = Long.MIN_VALUE;
        chainPrediction = true;
        predictionPending = true;
        predictionTick = tick;
        readyAnimation = false;
        predictedNextMoveIndex = nextMoveIndex;
        trackLocalAction(nextMoveIndex, tick, -1L);
    }

    /**
     * True when a COMPLETED stop is only the end of the move this client already chained out
     * of; the chained move keeps playing and the server's START for it follows.
     */
    static boolean absorbChainSourceStop(long revision, long tick) {
        if (!chainPrediction || revision != chainSourceRevision) {
            return false;
        }
        chainSourceStoppedTick = tick;
        return true;
    }

    /**
     * True when the server ended the move this client chained out of but no START for the
     * chained move arrived within {@code graceTicks}: the server did not chain.
     */
    static boolean chainPredictionAbandoned(long tick, int graceTicks) {
        return chainPrediction
                && chainSourceStoppedTick != Long.MIN_VALUE
                && tick - chainSourceStoppedTick > graceTicks;
    }

    static boolean chainPredictionPending() {
        return chainPrediction && predictionPending;
    }

    /** Move index of the local action with this server revision, or -1. */
    static int localMoveIndexFor(long revision) {
        return revision >= 0L && revision == localRevision ? localMoveIndex : -1;
    }

    static int localMoveIndex() {
        return localMoveIndex;
    }

    /** Whole ticks since the local action started; -1 without one. */
    static int actionTick(long tick) {
        if (localMoveIndex < 0) {
            return -1;
        }
        long elapsed = Math.max(0L, tick - localStartTick);
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    /**
     * True once per action when its timeline first reaches {@code cueTick}; cues already passed
     * (for example after an authoritative correction moved the start back) never replay.
     */
    static boolean reachCue(long tick, int cueTick) {
        int actionTick = actionTick(tick);
        if (actionTick < 0 || cueTick < 0) {
            return false;
        }
        return localCueTick < cueTick && actionTick >= cueTick;
    }

    static void markCuesThrough(long tick) {
        localCueTick = Math.max(localCueTick, actionTick(tick));
    }

    static boolean localClickBuffered() {
        return bufferedClick;
    }

    static int preparePrediction(long tick, int comboTimeoutTicks) {
        if (comboTimeoutTicks < 0) {
            throw new IllegalArgumentException("Combo timeout cannot be negative");
        }
        if (lastCompletedActionTick != Long.MIN_VALUE
                && tick > lastCompletedActionTick + comboTimeoutTicks) {
            predictedNextMoveIndex = 0;
            lastCompletedActionTick = Long.MIN_VALUE;
        }
        return predictedNextMoveIndex;
    }

    static void completeAction(long tick) {
        predictionPending = false;
        readyAnimation = false;
        localActionActive = false;
        lastCompletedActionTick = tick;
        clearLocalAction();
    }

    /**
     * Drops a pending prediction. A dropped chained prediction also ends the local action: the
     * move it chained out of has finished on the server, whose next move is the one predicted.
     */
    static void rejectPrediction() {
        if (chainPrediction) {
            long completed = chainSourceStoppedTick != Long.MIN_VALUE ? chainSourceStoppedTick : predictionTick;
            int next = localMoveIndex;
            localActionActive = false;
            lastCompletedActionTick = completed;
            clearLocalAction();
            predictedNextMoveIndex = Math.max(0, next);
        } else if (!localActionActive) {
            clearLocalAction();
        }
        predictionPending = false;
        readyAnimation = false;
    }

    static void clearActionAnimation() {
        predictedNextMoveIndex = 0;
        predictionPending = false;
        lastCompletedActionTick = Long.MIN_VALUE;
        readyAnimation = false;
        localActionActive = false;
        clearLocalAction();
    }

    private static void clearLocalAction() {
        localMoveIndex = -1;
        localStartTick = 0L;
        localRevision = -1L;
        localCueTick = -1;
        bufferedClick = false;
        chainPrediction = false;
        chainSourceRevision = -1L;
        chainSourceStoppedTick = Long.MIN_VALUE;
    }

    static void markReadyAnimation() {
        readyAnimation = true;
    }

    static CombatMode mode() {
        return mode;
    }

    static int predictedNextMoveIndex() {
        return predictedNextMoveIndex;
    }

    static boolean predictionPending() {
        return predictionPending;
    }

    static long predictionTick() {
        return predictionTick;
    }

    static boolean readyAnimation() {
        return readyAnimation;
    }

    static boolean localActionActive() {
        return localActionActive;
    }

    static void clear() {
        ACTION_REVISIONS.clear();
        mode = CombatMode.VANILLA;
        preferenceRevision = -1L;
        predictedNextMoveIndex = 0;
        predictionPending = false;
        predictionTick = 0L;
        lastCompletedActionTick = Long.MIN_VALUE;
        readyAnimation = false;
        localActionActive = false;
        clearLocalAction();
    }
}
