package com.example.myvillage.stats;

/** 攻防对抗先于招式/暴击，低防线性，高防平滑趋近保底，不产生负伤害。 */
public final class BodyDefense {
    private BodyDefense() { }
    public record Result(double attack, double defense, double penetration, double effectiveDefense,
                         double afterDefense, double retainedFraction) { }
    public static Result calculate(double attack, double defense, double penetration, CultivationStatsRules rules) {
        if (!Double.isFinite(attack) || !Double.isFinite(defense) || !Double.isFinite(penetration)
                || attack < 0 || defense < 0 || penetration < 0 || penetration > 1)
            throw new IllegalArgumentException("非法攻防参数");
        double d = defense * (1 - penetration), t = rules.transition(), p = rules.floor();
        double result;
        if (attack == 0) result = 0;
        else if (d <= attack * t) result = attack - d;
        else {
            double width = attack * (1 - t - p);
            result = attack * p + width * (width / (d + attack * (1 - 2 * t - p)));
        }
        return new Result(attack, defense, penetration, d, result, attack == 0 ? 0 : result / attack);
    }
}
