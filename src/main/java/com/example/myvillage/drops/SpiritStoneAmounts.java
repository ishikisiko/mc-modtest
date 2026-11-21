package com.example.myvillage.drops;

import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Quantity bounds before Looting, using the monster's major cultivation realm. */
public final class SpiritStoneAmounts {
    private static final Set<String> ITEMS = Set.of(
            "yuanxian:low_grade_spirit_stone", "yuanxian:medium_grade_spirit_stone", "yuanxian:high_grade_spirit_stone");
    private static final Curve CURVE = load();
    private static final int TARGET_LEVEL = StatsRules.bundled().definition(new RealmKey(CURVE.targetRealm(), 0)).order() + 1;

    private SpiritStoneAmounts() {}

    static boolean isStone(String item) { return ITEMS.contains(item); }
    static int countLimit() { return CURVE.countLimit(); }

    public record Bounds(int min, int max) {}

    private record Curve(@SerializedName("target_realm") String targetRealm, Bounds reference,
                         @SerializedName("at_qi_refining") Bounds first,
                         @SerializedName("at_target") Bounds target,
                         @SerializedName("count_limit") int countLimit) {}

    public static Bounds forRealm(int min, int max, int level) {
        if (level <= 0) return new Bounds(min, max);
        double progress = Math.min(1, (level - 1.0) / (TARGET_LEVEL - 1));
        return new Bounds(
                scale(min, CURVE.reference().min(), CURVE.first().min(), CURVE.target().min(), progress),
                scale(max, CURVE.reference().max(), CURVE.first().max(), CURVE.target().max(), progress));
    }

    private static int scale(int base, int reference, int first, int last, double progress) {
        double quantity = (double) base / reference * first * Math.pow((double) last / first, progress);
        return (int) Math.min(CURVE.countLimit(), Math.round(quantity));
    }

    private static Curve load() {
        try (var input = BuiltinResources.open("data/yuanxian/drops/spirit_stone_curve.json")) {
            if (input == null) throw new IllegalStateException("缺少灵石掉落曲线");
            Curve curve = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Curve.class);
            if (curve == null || curve.targetRealm() == null || curve.reference() == null || curve.first() == null || curve.target() == null
                    || curve.reference().min() <= 0 || curve.reference().max() < curve.reference().min()
                    || !orderedMultipliers(curve.reference(), curve.first()) || !orderedMultipliers(curve.reference(), curve.target())
                    || curve.first().min() > curve.target().min() || curve.first().max() > curve.target().max()
                    || curve.countLimit() < curve.target().max()
                    || StatsRules.bundled().definition(new RealmKey(curve.targetRealm(), 0)).order() < 1) {
                throw new IllegalStateException("灵石掉落曲线的范围或目标境界无效");
            }
            return curve;
        } catch (IOException error) {
            throw new IllegalStateException("无法读取灵石掉落曲线", error);
        }
    }

    private static boolean orderedMultipliers(Bounds reference, Bounds anchor) {
        return anchor.min() > 0 && (long) anchor.min() * reference.max() <= (long) anchor.max() * reference.min();
    }
}
