package com.example.myvillage.cultivation;

import com.example.myvillage.stats.CultivationStatsRules;
import com.example.myvillage.stats.StatsRules;

/** 档案品质和数表的共同适配器，两端读取同一份基础面板。 */
public final class CultivationStats {
    private CultivationStats() { }
    public static CultivationStatsRules.Body base(CultivationProfile profile) {
        var key = StatsRules.bundled().playerKey(profile.realmId().toString(), profile.stageId().toString());
        return CultivationStatsRules.bundled().at(key, profile.foundationQuality(), profile.goldenCoreQuality());
    }
    public static java.util.Optional<CultivationStatsRules.Body> find(CultivationProfile profile) {
        try { return java.util.Optional.of(base(profile)); }
        catch (IllegalArgumentException e) { return java.util.Optional.empty(); }
    }
    public static String foundationName(int quality) {
        return switch (quality) {
            case 0 -> "尚未筑基";
            case 1 -> "地道下品";
            case 2 -> "地道中品";
            case 3 -> "地道上品";
            case 4 -> "天道筑基";
            default -> throw new IllegalArgumentException("非法根基品质");
        };
    }
    public static String goldenName(int quality) {
        return switch (quality) {
            case 0 -> "尚未结丹";
            case 1 -> "下品金丹";
            case 2 -> "中品金丹";
            case 3 -> "上品金丹";
            default -> throw new IllegalArgumentException("非法金丹品质");
        };
    }
}
