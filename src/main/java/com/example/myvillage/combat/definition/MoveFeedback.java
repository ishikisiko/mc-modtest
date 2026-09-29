package com.example.myvillage.combat.definition;

/**
 * Presentation-only cues for one move. Nothing here affects hits or damage. The server reads
 * {@code hitStopTicks} only to size the matching target freeze, which is a reaction, not timing.
 */
public record MoveFeedback(
        SwingSound swingSound,
        float swingPitch,
        boolean heavyHit,
        float hitStopTicks,
        float cameraTrauma,
        float cutRollDegrees) {
    public MoveFeedback {
        if (swingSound == null) {
            throw new IllegalArgumentException("Swing sound is required");
        }
        if (!(swingPitch >= 0.5F && swingPitch <= 2.0F)) {
            throw new IllegalArgumentException("Swing pitch must be in 0.5..2.0");
        }
        if (!(hitStopTicks >= 0.0F && hitStopTicks <= 6.0F)) {
            throw new IllegalArgumentException("Hit-stop must be in 0..6 ticks");
        }
        if (!(cameraTrauma >= 0.0F && cameraTrauma <= 1.0F)) {
            throw new IllegalArgumentException("Camera trauma must be in 0..1");
        }
        if (!(cutRollDegrees >= -180.0F && cutRollDegrees <= 180.0F)) {
            throw new IllegalArgumentException("Cut roll must be in -180..180 degrees");
        }
    }

    public enum SwingSound {
        CUT,
        THRUST
    }
}
