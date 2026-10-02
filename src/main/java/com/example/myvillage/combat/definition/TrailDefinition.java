package com.example.myvillage.combat.definition;

import java.util.List;
import java.util.Objects;

/**
 * A move's optional world-trail path ({@code trail} in the style file): the samples the
 * third-person trail follows instead of the move's hit samples, in the same form and under the
 * same sample rules. Presentation only: the server never reads it, so hits, damage, timing and
 * the active samples always come from {@link HitboxDefinition}.
 */
public record TrailDefinition(List<HitboxSample> samples) {
    public TrailDefinition {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Trail samples are required");
        }
    }
}
