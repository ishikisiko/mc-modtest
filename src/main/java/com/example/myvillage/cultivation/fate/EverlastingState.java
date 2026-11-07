package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigInteger;

/** 半点份数和未完成份数的整数分子；换境界只改变后续每 tick 的收入。 */
public record EverlastingState(Counter health, Counter defense, Counter attack, long yearTicks) {
    public record Counter(long units, long remainder) {
        public static final Counter ZERO = new Counter(0, 0);
        public static final Codec<Counter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("units").forGetter(Counter::units),
                Codec.LONG.fieldOf("remainder").forGetter(Counter::remainder)).apply(i, Counter::new));
        public Counter {
            if (units < 0 || remainder < 0) throw new IllegalArgumentException("非法长生累计值");
        }
        public double points(double step) { return units * step; }
    }
    public static final EverlastingState EMPTY = new EverlastingState(Counter.ZERO, Counter.ZERO, Counter.ZERO, 1);
    public static final Codec<EverlastingState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Counter.CODEC.fieldOf("health").forGetter(EverlastingState::health),
            Counter.CODEC.fieldOf("defense").forGetter(EverlastingState::defense),
            Counter.CODEC.fieldOf("attack").forGetter(EverlastingState::attack),
            Codec.LONG.fieldOf("year_ticks").forGetter(EverlastingState::yearTicks)).apply(i, EverlastingState::new));
    public EverlastingState {
        java.util.Objects.requireNonNull(health); java.util.Objects.requireNonNull(defense); java.util.Objects.requireNonNull(attack);
        if (yearTicks <= 0 || health.remainder >= yearTicks || defense.remainder >= yearTicks || attack.remainder >= yearTicks)
            throw new IllegalArgumentException("非法长生成长进度");
    }
    public boolean sameCredits(EverlastingState other) {
        return health.units == other.health.units && defense.units == other.defense.units && attack.units == other.attack.units;
    }
    public EverlastingState advance(EverlastingRules.Annual annual, long nextYearTicks, long ticks) {
        if (nextYearTicks <= 0 || ticks < 0) throw new IllegalArgumentException("非法长生时间");
        if (annual.empty() || ticks == 0) return this;
        return new EverlastingState(advance(health, annual.health(), nextYearTicks, ticks),
                advance(defense, annual.defense(), nextYearTicks, ticks),
                advance(attack, annual.attack(), nextYearTicks, ticks), nextYearTicks);
    }
    private Counter advance(Counter old, long rate, long denominator, long ticks) {
        if (rate < 0) throw new IllegalArgumentException("非法长生成长速度");
        long remainder = yearTicks == denominator ? old.remainder
                : BigInteger.valueOf(old.remainder).multiply(BigInteger.valueOf(denominator))
                        .divide(BigInteger.valueOf(yearTicks)).longValueExact();
        if (rate == 0) return new Counter(old.units, remainder);
        if (ticks <= (Long.MAX_VALUE - remainder) / rate) {
            long numerator = remainder + rate * ticks;
            return new Counter(com.example.myvillage.cultivation.time.CultivationTimeMath.saturatingAdd(
                    old.units, numerator / denominator), numerator % denominator);
        }
        var result = BigInteger.valueOf(rate).multiply(BigInteger.valueOf(ticks)).add(BigInteger.valueOf(remainder))
                .divideAndRemainder(BigInteger.valueOf(denominator));
        long earned = result[0].min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        return new Counter(com.example.myvillage.cultivation.time.CultivationTimeMath.saturatingAdd(old.units, earned),
                result[1].longValueExact());
    }
}
