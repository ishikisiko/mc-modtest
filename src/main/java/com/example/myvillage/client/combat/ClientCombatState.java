package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;

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
    }

    static void rejectPrediction() {
        predictionPending = false;
        readyAnimation = false;
    }

    static void clearActionAnimation() {
        predictedNextMoveIndex = 0;
        predictionPending = false;
        lastCompletedActionTick = Long.MIN_VALUE;
        readyAnimation = false;
        localActionActive = false;
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
    }
}
