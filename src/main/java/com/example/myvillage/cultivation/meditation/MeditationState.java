package com.example.myvillage.cultivation.meditation;

public enum MeditationState {
    IDLE,
    PREPARING_NORMAL,
    PREPARING_SPIRIT,
    MEDITATING_NORMAL,
    MEDITATING_SPIRIT,
    ADVANCING_ORDINARY,
    ADVANCING_BOTTLENECK;

    public static MeditationState preparing(MeditationMode mode) {
        return mode == MeditationMode.NORMAL ? PREPARING_NORMAL : PREPARING_SPIRIT;
    }

    public static MeditationState meditating(MeditationMode mode) {
        return mode == MeditationMode.NORMAL ? MEDITATING_NORMAL : MEDITATING_SPIRIT;
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
