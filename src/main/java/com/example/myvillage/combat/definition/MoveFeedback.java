package com.example.myvillage.combat.definition;

/**
 * Presentation-only cues for one move. Nothing here affects timing, hits, or damage.
 */
public record MoveFeedback(SwingSound swingSound, float swingPitch, boolean heavyHit) {
    public MoveFeedback {
        if (swingSound == null) {
            throw new IllegalArgumentException("Swing sound is required");
        }
        if (!(swingPitch >= 0.5F && swingPitch <= 2.0F)) {
            throw new IllegalArgumentException("Swing pitch must be in 0.5..2.0");
        }
    }

    public enum SwingSound {
        CUT,
        THRUST
    }
}
