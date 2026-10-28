package com.example.myvillage.worldplan.capital;

import com.example.myvillage.worldplan.WorldPlan;

/**
 * Explicit read-only compatibility boundary. Reading an already-created WorldPlan does not rerun
 * its constructor, change its rules, infer eight cities, or migrate a saved site or warehouse.
 */
public final class LegacyCapitalAdapter {
    private LegacyCapitalAdapter() {}

    /** Dimensions retain the old integer cell-count semantics; no new preview transform is inferred. */
    public record Snapshot(String algorithm, String legacyRuleId, String hostRegionId,
                           int x, int baseY, int z, int width, int height, int depth,
                           String legacyLayoutHash, String legacyLifeLayout) {}

    public static Snapshot read(WorldPlan plan) {
        var city = plan.city();
        var rules = plan.rules().city;
        return new Snapshot(plan.rules().algorithm, rules.id(), city.host(), city.x(), city.baseY(), city.z(),
                city.width(), city.height(), city.depth(), rules.layoutHash(), rules.lifeLayout());
    }
}
