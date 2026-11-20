package com.example.myvillage.stats;

/** 攻防对抗先于招式/暴击，低防线性，高防平滑趋近保底，不产生负伤害。 */
public final class BodyDefense {
    private BodyDefense() { }
    public record Result(double attack, double defense, double penetration, double flatPenetration, double effectiveDefense,
                         double afterDefense, double retainedFraction) { }
    public static Result calculate(double attack, double defense, double penetration, CultivationStatsRules rules) {
        return calculate(attack, defense, penetration, 0, rules);
    }

    public static Result calculate(double attack, double defense, double penetration, double flatPenetration,
                                   CultivationStatsRules rules) {
        if (!Double.isFinite(attack) || !Double.isFinite(defense) || !Double.isFinite(penetration)
                || !Double.isFinite(flatPenetration) || attack < 0 || defense < 0
                || penetration < 0 || penetration > 1 || flatPenetration < 0)
            throw new IllegalArgumentException("非法攻防参数");
        double d = Math.max(0, defense * (1 - penetration) - flatPenetration);
        double t = rules.transition(), p = rules.floor();
        double result;
        if (attack == 0) result = 0;
        else if (d <= attack * t) result = attack - d;
        else {
            double width = attack * (1 - t - p);
            result = attack * p + width * (width / (d + attack * (1 - 2 * t - p)));
        }
        return new Result(attack, defense, penetration, flatPenetration, d, result, attack == 0 ? 0 : result / attack);
    }
}
