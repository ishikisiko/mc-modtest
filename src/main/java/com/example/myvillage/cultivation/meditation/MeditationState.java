package com.example.myvillage.cultivation.meditation;

public enum MeditationState {
    IDLE,
    PREPARING_NORMAL,
    PREPARING_SPIRIT,
    MEDITATING_NORMAL,
    MEDITATING_SPIRIT,
    ADVANCING_ORDINARY,
    ADVANCING_BOTTLENECK;

    /** Study sits like normal meditation; its status carries the study progress instead. */
    public static MeditationState preparing(MeditationMode mode) {
        return switch (mode) {
            case NORMAL, STUDY -> PREPARING_NORMAL;
            case SPIRIT -> PREPARING_SPIRIT;
        };
    }

    public static MeditationState meditating(MeditationMode mode) {
        return switch (mode) {
            case NORMAL, STUDY -> MEDITATING_NORMAL;
            case SPIRIT -> MEDITATING_SPIRIT;
        };
    }

    public boolean preparing() {
        return this == PREPARING_NORMAL || this == PREPARING_SPIRIT;
    }

    public boolean meditating() {
        return this == MEDITATING_NORMAL || this == MEDITATING_SPIRIT;
    }

    public boolean advancing() {
        return this == ADVANCING_ORDINARY || this == ADVANCING_BOTTLENECK;
    }

    public boolean active() {
        return this != IDLE;
    }
}
