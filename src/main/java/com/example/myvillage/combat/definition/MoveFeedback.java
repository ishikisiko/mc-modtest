package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Presentation-only cues for one move. Nothing here affects hits or damage. The server reads
 * {@code hitStopTicks} only to size the matching target freeze, which is a reaction, not timing.
 * Sounds are sound-event ids resolved through the sound registry; {@code heavyLayerSound} is an
 * extra layer played with the hit sound, and {@code heavyHit} selects the heavy particle and spark
 * count.
 */
public record MoveFeedback(
        ResourceLocation swingSound,
        float swingPitch,
        ResourceLocation hitSound,
        Optional<ResourceLocation> heavyLayerSound,
        boolean heavyHit,
        float hitStopTicks,
        float cameraTrauma,
        float cutRollDegrees) {
    public MoveFeedback {
        Objects.requireNonNull(swingSound, "swingSound");
        Objects.requireNonNull(hitSound, "hitSound");
        heavyLayerSound = Objects.requireNonNull(heavyLayerSound, "heavyLayerSound");
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
}
