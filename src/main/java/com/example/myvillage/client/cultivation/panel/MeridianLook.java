package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.meditation.MeditationState;

/**
 * How the meridian diagram looks for one session state. Everything here is presentation: the
 * state is the server's {@link MeditationState}, and {@code channelsOpen} is whether the synced
 * profile has an awakened root and Basic Breathing. Quantities the diagram shows (progress,
 * stability, advancement time) are read separately from the panel context; motion speeds and
 * mote counts below are decoration and stand for no gameplay value.
 */
public record MeridianLook(
        Phase phase,
        int color,
        float figureLight,
        float auraLevel,
        float circuitLevel,
        float limbLevel,
        int circuitMotes,
        float circuitPeriodSeconds,
        int limbMotes,
        boolean gathering,
        boolean halo,
        boolean barrier) {
    public enum Phase {
        /** No root or no breathing technique: the channels are closed. */
        DORMANT,
        /** Channels open, no session. */
        REST,
        /** The server announced a preparing session. */
        PREPARING,
        MEDITATING,
        ADVANCING
    }

    public static final int DORMANT_COLOR = 0xFF56625E;
    public static final int REST_COLOR = 0xFF4F9A8A;
    public static final int NORMAL_COLOR = 0xFF74DEC2;
    public static final int SPIRIT_COLOR = 0xFF8EC6FF;
    public static final int ADVANCE_COLOR = 0xFFF2D68A;
    public static final int BOTTLENECK_COLOR = 0xFFF0805A;

    /** How far a bottleneck slows the qi at its pass: 0 is even flow, 1 would stop it there. */
    static final double BARRIER_SQUEEZE = 0.78D;

    /** The look for a session state; a missing status (not yet synchronized) looks like rest. */
    public static MeridianLook of(MeditationState state, boolean channelsOpen) {
        MeditationState current = state == null ? MeditationState.IDLE : state;
        if (!channelsOpen && !current.active()) {
            return new MeridianLook(Phase.DORMANT, DORMANT_COLOR, 0.62F, 0.0F, 0.10F, 0.06F,
                    0, 0.0F, 0, false, false, false);
        }
        return switch (current) {
            case IDLE -> new MeridianLook(Phase.REST, REST_COLOR, 0.80F, 0.05F, 0.38F, 0.20F,
                    0, 0.0F, 0, false, false, false);
            case PREPARING_NORMAL -> new MeridianLook(Phase.PREPARING, NORMAL_COLOR, 0.88F, 0.12F, 0.50F, 0.26F,
                    0, 0.0F, 0, true, false, false);
            case PREPARING_SPIRIT -> new MeridianLook(Phase.PREPARING, SPIRIT_COLOR, 0.88F, 0.14F, 0.50F, 0.34F,
                    0, 0.0F, 0, true, false, false);
            case MEDITATING_NORMAL -> new MeridianLook(Phase.MEDITATING, NORMAL_COLOR, 1.0F, 0.16F, 1.0F, 0.32F,
                    6, 6.0F, 0, false, false, false);
            case MEDITATING_SPIRIT -> new MeridianLook(Phase.MEDITATING, SPIRIT_COLOR, 1.0F, 0.24F, 1.0F, 0.85F,
                    9, 4.2F, 2, false, false, false);
            case ADVANCING_ORDINARY -> new MeridianLook(Phase.ADVANCING, ADVANCE_COLOR, 1.0F, 0.26F, 1.0F, 0.55F,
                    12, 2.8F, 0, false, true, false);
            case ADVANCING_BOTTLENECK -> new MeridianLook(Phase.ADVANCING, BOTTLENECK_COLOR, 1.0F, 0.26F, 1.0F, 0.55F,
                    12, 3.4F, 0, false, true, true);
        };
    }

    /** Stroke intensity (0..1) of a channel of {@code vessel}. */
    public float level(MeridianChart.Vessel vessel) {
        return switch (vessel) {
            case GOVERNING, CONCEPTION -> circuitLevel;
            case HAND, FOOT -> limbLevel;
        };
    }

    public boolean flowing() {
        return circuitMotes > 0 && circuitPeriodSeconds > 0.0F;
    }

    /**
     * Where mote {@code mote} of {@code circuitMotes} is on the small circuit (0..1 of its length,
     * starting at the perineum) after {@code seconds}. The motes are evenly spaced and lap once
     * per period; with a barrier they bunch up and slow down at {@code barrierAt}.
     */
    public double circuitPosition(double seconds, int mote, double barrierAt) {
        if (!flowing()) {
            return 0.0D;
        }
        double even = seconds / circuitPeriodSeconds + mote / (double) circuitMotes;
        double lap = even - Math.floor(even);
        return barrier ? squeeze(lap, barrierAt, BARRIER_SQUEEZE) : lap;
    }

    /**
     * A monotonic warp of the lap position {@code lap} (0..1, wrapping) that moves slowest where
     * the result is {@code at}: {@code at} maps to itself with slope {@code 1 - strength}, and the
     * point half a lap away maps to itself with slope {@code 1 + strength}. One lap in is one lap
     * out, so a lap still takes one period.
     */
    static double squeeze(double lap, double at, double strength) {
        double warped = lap - strength / (2.0D * Math.PI) * Math.sin(2.0D * Math.PI * (lap - at));
        return warped - Math.floor(warped);
    }
}
