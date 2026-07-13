package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementKind;

import java.util.Objects;
import java.util.Optional;

public record MeditationStatus(
        MeditationState state,
        int preparationTicksRemaining,
        MeditationStopReason reason,
        Optional<AdvancementKind> advancementKind,
        int advancementDurationTicks,
        int advancementTicksRemaining) {
    public MeditationStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        advancementKind = Objects.requireNonNull(advancementKind, "advancementKind");
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
    }

    public MeditationStatus(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason) {
        this(state, preparationTicksRemaining, reason, Optional.empty(), 0, 0);
    }

    public static MeditationStatus idle(MeditationStopReason reason) {
        return new MeditationStatus(MeditationState.IDLE, 0, reason);
    }
}
