package com.example.myvillage.region.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Shared Gson instance configured to reproduce the offline generator's
 * canonical JSON form ({@code indent=2}). Sorted key order is achieved by
 * serializing {@link java.util.TreeMap} instances (Gson follows a map's
 * iteration order), not via a Gson key-ordering policy.
 *
 * <p>Double serialization uses {@code Double.toString} (shortest round-trip
 * representation), matching Python's {@code repr(float)} / {@code json.dumps}
 * for the magnitude of values that appear in a region graph (positions in
 * {@code [-1.4, 1.4]}, integer counts, tier values).
 */
final class RegionJson {
    /** Canonical pretty printer: 2-space indent, each array element on its own line. */
    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private RegionJson() {
    }
}
