package com.example.myvillage.sim.cultivation;

import java.util.OptionalLong;

/** 玩家与 NPC 共用的有界灵力运算；状态写入由各自唯一服务负责。 */
public final class SpiritualPowerMath {
    public static long capacity(long baseCap,double bonus) {
        if(baseCap<0||!Double.isFinite(bonus)||bonus<0)throw new IllegalArgumentException("Invalid spiritual-power capacity bonus");
        return java.math.BigDecimal.valueOf(baseCap).multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(bonus)))
                .min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).setScale(0,java.math.RoundingMode.DOWN).longValueExact();
    }
    private SpiritualPowerMath() { }
    public static long clamp(long current, long cap) {
        if (current < 0 || cap < 0) throw new IllegalArgumentException("灵力不能为负");
        return Math.min(current, cap);
    }
    public static OptionalLong spend(long current, long cap, long cost) {
        if (cost < 0) throw new IllegalArgumentException("灵力消耗不能为负");
        long available = clamp(current, cap);
        return available < cost ? OptionalLong.empty() : OptionalLong.of(available - cost);
    }
    public static long recover(long current, long cap, long amount) {
        if (amount < 0) throw new IllegalArgumentException("灵力恢复不能为负");
        long available = clamp(current, cap);
        return available + Math.min(amount, cap - available);
    }
}
