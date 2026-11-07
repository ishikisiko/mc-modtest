package com.example.myvillage.cultivation.fate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 战斗暂态的纯运算；各类共享容量，只有后续攻击才能使用新适应。 */
public record JingangAdaptation(List<Double> points, long lastCombat, long lastUpdate) {
    public JingangAdaptation {
        points=List.copyOf(points);
        if (points.size()!=AdaptationCategory.values().length || points.stream().anyMatch(v -> !Double.isFinite(v)||v<0))
            throw new IllegalArgumentException("非法金刚适应");
    }
    public static JingangAdaptation empty(long now) {
        return new JingangAdaptation(Collections.nCopies(AdaptationCategory.values().length,0.0),now,now);
    }
    public double total() { return points.stream().mapToDouble(Double::doubleValue).sum(); }
    public double points(AdaptationCategory category) { return points.get(category.ordinal()); }
    public JingangAdaptation touch(long now) { return new JingangAdaptation(points,now,now); }
    public JingangAdaptation grow(AdaptationCategory category, double amount, long now, SpiritBodyRules.Adaptation rules) {
        if (!Double.isFinite(amount)||amount<0) throw new IllegalArgumentException("非法适应增量");
        var decayed=tick(now,rules); var next=new ArrayList<>(decayed.points);
        double old=next.get(category.ordinal()), addition=Math.min(amount, Math.max(0,rules.capacity()-old));
        double others=Math.max(0,decayed.total()-old), overflow=Math.max(0,decayed.total()+addition-rules.capacity());
        double retained=others<=0?0:Math.max(0,(others-overflow)/others);
        for (var c:AdaptationCategory.values()) if(c!=category) next.set(c.ordinal(),next.get(c.ordinal())*retained);
        next.set(category.ordinal(),old+addition);
        return new JingangAdaptation(next,now,now);
    }
    public JingangAdaptation tick(long now, SpiritBodyRules.Adaptation rules) {
        if(now<lastUpdate) return empty(now);
        long start=Math.max(lastUpdate,lastCombat+rules.combatTimeoutTicks());
        double loss=Math.max(0,now-start)*rules.capacity()/rules.decayTicks(), total=total();
        if(loss<=0 || total<=0) return new JingangAdaptation(points,lastCombat,now);
        double factor=Math.max(0,(total-loss)/total);
        return new JingangAdaptation(points.stream().map(v->v*factor).toList(),lastCombat,now);
    }
    public double defense(AdaptationCategory category,double bodyDefense,SpiritBodyRules.Adaptation rules) {
        return Math.max(0,bodyDefense)*points(category)*rules.defenseFractionPerPoint();
    }
}
