package com.example.myvillage.wilderness;

import com.example.myvillage.stats.RealmKey;
import java.util.Objects;

/** Immutable first assignment, retained even if the beast later crosses a region or dimension. */
public record BeastBirth(RealmKey realm, String region, int x, int y, int z, boolean natural) {
    public BeastBirth {
        Objects.requireNonNull(realm); Objects.requireNonNull(region);
        if (region.isBlank()) throw new IllegalArgumentException("Empty birth region");
    }
}
