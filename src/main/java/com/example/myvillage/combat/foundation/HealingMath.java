package com.example.myvillage.combat.foundation;

/** 不接触实体、护甲或存档的治疗计算。 */
public final class HealingMath {
    private HealingMath() { }

    /** 吸收不计入，过量按受击前生命封顶，附加伤害按主伤害比例剔除。 */
    public static double primaryLoss(double before, double after, double primaryFraction) {
        if (!positive(before) || !Double.isFinite(after) || !positive(primaryFraction) || primaryFraction > 1) return 0;
        return Math.min(before, Math.max(0, before - Math.max(0, after))) * primaryFraction;
    }

    public static double lifesteal(double actualPrimaryLoss, double fraction) {
        if (!positive(actualPrimaryLoss) || !positive(fraction) || fraction > 1) return 0;
        return Math.min(Float.MAX_VALUE, actualPrimaryLoss * fraction);
    }

    /** 零血不治疗；非法、负数及无穷输入不得转成收益。 */
    public static double adjusted(double amount, double multiplier, double health, double maximumHealth,
            double limit) {
        if (!positive(amount) || !positive(multiplier) || !positive(health) || !positive(maximumHealth)
                || !positive(limit) || maximumHealth <= health) return 0;
        double missing = maximumHealth - health;
        return Math.min(Math.min(missing, limit), Math.min(Float.MAX_VALUE, amount * multiplier));
    }

    /** 避免 float 向上舍入令最终实得量超过吸血剩余额度。 */
    public static float floorFloat(double value) {
        if (!positive(value)) return 0;
        float rounded = (float) Math.min(value, Float.MAX_VALUE);
        return rounded > value ? Math.nextDown(rounded) : rounded;
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
