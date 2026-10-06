package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementKind;

import java.util.Objects;
import java.util.Optional;

/**
 * The server's session status as the client sees it. {@code study} is present only while a study (研读)
 * session runs, whose states are {@link MeditationState#PREPARING_NORMAL} and
 * {@link MeditationState#MEDITATING_NORMAL}; a normal-meditation status has it empty.
 */
public record MeditationStatus(
        MeditationState state,
        int preparationTicksRemaining,
        MeditationStopReason reason,
        Optional<AdvancementKind> advancementKind,
        int advancementDurationTicks,
        int advancementTicksRemaining,
        Optional<StudyProgress> study) {
    public MeditationStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        advancementKind = Objects.requireNonNull(advancementKind, "advancementKind");
        study = Objects.requireNonNull(study, "study");
        if (preparationTicksRemaining < 0
                || preparationTicksRemaining > MeditationManager.PREPARATION_TICKS) {
            throw new IllegalArgumentException(
                    "Preparation ticks remaining must be in 0.."
                            + MeditationManager.PREPARATION_TICKS);
        }
        if (!state.preparing() && preparationTicksRemaining != 0) {
            throw new IllegalArgumentException(
                    "Only preparation states may expose preparation ticks");
        }
        if (state.advancing()) {
            if (advancementKind.isEmpty()) {
                throw new IllegalArgumentException("Advancement states must expose their kind");
            }
            if (advancementDurationTicks <= 0
                    || advancementTicksRemaining < 0
                    || advancementTicksRemaining > advancementDurationTicks) {
                throw new IllegalArgumentException(
                        "Advancement duration must be positive and remaining ticks must be in range");
            }
            AdvancementKind expectedKind = state == MeditationState.ADVANCING_ORDINARY
                    ? AdvancementKind.ORDINARY
                    : AdvancementKind.BOTTLENECK;
            if (advancementKind.orElseThrow() != expectedKind) {
                throw new IllegalArgumentException(
                        "Advancement state and kind must describe the same rule");
            }
        } else if (advancementKind.isPresent()
                || advancementDurationTicks != 0
                || advancementTicksRemaining != 0) {
            throw new IllegalArgumentException(
                    "Only advancement states may expose advancement timing");
        }
        if (study.isPresent()
                && state != MeditationState.PREPARING_NORMAL
                && state != MeditationState.MEDITATING_NORMAL) {
            throw new IllegalArgumentException("Only study states may expose study progress");
        }
    }

    public MeditationStatus(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason,
            Optional<AdvancementKind> advancementKind,
            int advancementDurationTicks,
            int advancementTicksRemaining) {
        this(state, preparationTicksRemaining, reason, advancementKind,
                advancementDurationTicks, advancementTicksRemaining, Optional.empty());
    }

    public MeditationStatus(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason) {
        this(state, preparationTicksRemaining, reason, Optional.empty(), 0, 0);
    }

    /** A study (研读) status: preparing or reading, with its progress. */
    public static MeditationStatus study(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason,
            StudyProgress progress) {
        return new MeditationStatus(state, preparationTicksRemaining, reason,
                Optional.empty(), 0, 0, Optional.of(Objects.requireNonNull(progress, "progress")));
    }

    public static MeditationStatus idle(MeditationStopReason reason) {
        return new MeditationStatus(MeditationState.IDLE, 0, reason);
    }

    /** True while a study session (preparing or reading) runs. */
    public boolean studying() {
        return study.isPresent();
    }
}
