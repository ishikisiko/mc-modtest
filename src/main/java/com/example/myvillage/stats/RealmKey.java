package com.example.myvillage.stats;

import java.util.Objects;

/** Canonical realm identity with a zero-based stage; membership is checked by StatsRules. */
public record RealmKey(String realmId, int stage) {
    public RealmKey {
        realmId = normalizeId(realmId);
        if (stage < 0) throw new IllegalArgumentException("realm.stage must be nonnegative");
    }

    public static String normalizeId(String id) {
        Objects.requireNonNull(id, "realm.id");
        if (!id.contains(":")) id = "myvillage:" + id;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid realm.id: " + id);
        return id;
    }
}
