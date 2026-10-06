package com.example.myvillage.cultivation.meditation;

/**
 * NORMAL and SPIRIT cultivate; STUDY reads a technique manual (研读). A study session sits like normal
 * meditation (its states are {@link MeditationState#PREPARING_NORMAL} and
 * {@link MeditationState#MEDITATING_NORMAL}) and is told apart by {@link MeditationStatus#study()}.
 */
public enum MeditationMode {
    NORMAL,
    SPIRIT,
    STUDY
}
