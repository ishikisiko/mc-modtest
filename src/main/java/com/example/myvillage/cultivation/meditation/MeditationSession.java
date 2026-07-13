package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementKind;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

final class MeditationSession {
    private MeditationState state;
    private MeditationMode mode;
    private final AdvancementContext advancement;
    private final ResourceLocation sourceRealm;
    private final ResourceLocation sourceStage;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;
    private final ResourceKey<Level> dimension;
    private int preparationTicks;
    private int activeMeditationTicks;
    private int advancementTicks;
    private BasicBreathingSettlement.Remainders settlementRemainders =
            BasicBreathingSettlement.Remainders.ZERO;
    private long lastDuplicateFeedbackTick = Long.MIN_VALUE;

    MeditationSession(
            MeditationMode mode,
            double anchorX,
            double anchorY,
            double anchorZ,
            ResourceKey<Level> dimension) {
        this(mode, null, null, anchorX, anchorY, anchorZ, dimension);
    }

    MeditationSession(
            MeditationMode mode,
            ResourceLocation sourceRealm,
            ResourceLocation sourceStage,
            double anchorX,
            double anchorY,
            double anchorZ,
            ResourceKey<Level> dimension) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.advancement = null;
        this.sourceRealm = sourceRealm;
        this.sourceStage = sourceStage;
        this.state = MeditationState.preparing(mode);
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    private MeditationSession(
            AdvancementContext advancement,
            double anchorX,
            double anchorY,
            double anchorZ,
            ResourceKey<Level> dimension) {
        this.mode = null;
        this.advancement = Objects.requireNonNull(advancement, "advancement");
        this.sourceRealm = advancement.sourceRealm();
        this.sourceStage = advancement.sourceStage();
        this.state = advancement.definition().kind() == AdvancementKind.ORDINARY
                ? MeditationState.ADVANCING_ORDINARY
                : MeditationState.ADVANCING_BOTTLENECK;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    static MeditationSession advancement(
            AdvancementContext advancement,
            double anchorX,
            double anchorY,
            double anchorZ,
            ResourceKey<Level> dimension) {
        return new MeditationSession(advancement, anchorX, anchorY, anchorZ, dimension);
    }

    MeditationState state() {
        return state;
    }

    ResourceKey<Level> dimension() {
        return dimension;
    }

    Optional<AdvancementContext> advancementContext() {
        return Optional.ofNullable(advancement);
    }

    boolean matchesSource(ResourceLocation realm, ResourceLocation stage) {
        return (sourceRealm == null || sourceRealm.equals(realm))
                && (sourceStage == null || sourceStage.equals(stage));
    }

    boolean moved(double x, double y, double z) {
        return Math.abs(x - anchorX) > MeditationManager.MOVEMENT_TOLERANCE
                || Math.abs(y - anchorY) > MeditationManager.MOVEMENT_TOLERANCE
                || Math.abs(z - anchorZ) > MeditationManager.MOVEMENT_TOLERANCE;
    }

    boolean advancePreparation() {
        if (!state.preparing()) {
            return false;
        }
        preparationTicks++;
        if (preparationTicks >= MeditationManager.PREPARATION_TICKS) {
            state = MeditationState.meditating(mode);
            return true;
        }
        return false;
    }

    boolean advanceMeditationTick() {
        if (!state.meditating()) {
            return false;
        }
        activeMeditationTicks++;
        if (activeMeditationTicks >= BasicBreathingSettlement.SETTLEMENT_INTERVAL_TICKS) {
            activeMeditationTicks = 0;
            return true;
        }
        return false;
    }

    BasicBreathingSettlement.Remainders settlementRemainders() {
        return settlementRemainders;
    }

    void settlementSucceeded(BasicBreathingSettlement.Remainders remainders) {
        settlementRemainders = Objects.requireNonNull(remainders, "remainders");
    }

    void downgradeToNormal() {
        if (state != MeditationState.MEDITATING_SPIRIT || mode != MeditationMode.SPIRIT) {
            throw new IllegalStateException("Only active spirit meditation can downgrade");
        }
        mode = MeditationMode.NORMAL;
        state = MeditationState.MEDITATING_NORMAL;
    }

    MeditationMode mode() {
        if (mode == null) {
            throw new IllegalStateException("Advancement sessions do not have a meditation mode");
        }
        return mode;
    }

    boolean advanceAdvancementTick() {
        if (!state.advancing()) {
            return false;
        }
        advancementTicks++;
        return advancementTicks >= advancement.definition().durationTicks();
    }

    boolean shouldReportAdvancementProgress() {
        return state.advancing()
                && advancementTicks > 0
                && advancementTicks < advancement.definition().durationTicks()
                && advancementTicks % MeditationManager.ADVANCEMENT_FEEDBACK_INTERVAL_TICKS == 0;
    }

    int advancementTicksRemaining() {
        return state.advancing()
                ? Math.max(0, advancement.definition().durationTicks() - advancementTicks)
                : 0;
    }

    int preparationTicksRemaining() {
        return state.preparing()
                ? MeditationManager.PREPARATION_TICKS - preparationTicks
                : 0;
    }

    boolean allowDuplicateFeedback(long serverTick) {
        if (lastDuplicateFeedbackTick == Long.MIN_VALUE
                || serverTick - lastDuplicateFeedbackTick >= MeditationManager.DUPLICATE_FEEDBACK_INTERVAL_TICKS) {
            lastDuplicateFeedbackTick = serverTick;
            return true;
        }
        return false;
    }

    MeditationStatus status(MeditationStopReason reason) {
        if (state.advancing()) {
            return new MeditationStatus(
                    state,
                    0,
                    reason,
                    Optional.of(advancement.definition().kind()),
                    advancement.definition().durationTicks(),
                    advancementTicksRemaining());
        }
        return new MeditationStatus(state, preparationTicksRemaining(), reason);
    }
}
