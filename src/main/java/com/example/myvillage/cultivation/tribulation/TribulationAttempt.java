package com.example.myvillage.cultivation.tribulation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 仅在线加载 tick 推进。身份、规则、伤害基准与受击次数均冻结；不存 registry 数字 ID。 */
public record TribulationAttempt(int schemaVersion, UUID id, UUID owner, String dimension,
        double x, double y, double z, long cultivationCap, int requiredStability, float healthBasis, int acceptedHits,
        TribulationRules rules, List<Mark> plan, Clock clock) {
    public enum Phase { PREPARING, ACTIVE, RECOVERING, SUCCEEDED, FAILED, STOPPED }
    public record Mark(double x, double y, double z) {
        public Mark {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("落点无效");
        }
    }
    public record Clock(Phase phase, int preparation, int elapsed, int nextStrike, int outside,
            int recovery, boolean paused, String result, boolean penaltyApplied) {
        public Clock {
            Objects.requireNonNull(phase); Objects.requireNonNull(result);
            if (preparation < 0 || elapsed < 0 || nextStrike < 0 || outside < 0 || recovery < 0)
                throw new IllegalArgumentException("雷劫时钟无效");
        }
    }
    public TribulationAttempt {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(dimension);
        Objects.requireNonNull(rules); Objects.requireNonNull(clock);
        plan = List.copyOf(plan);
        if (schemaVersion != 2 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || cultivationCap < 1 || requiredStability < 0 || !Float.isFinite(healthBasis) || healthBasis <= 0
                || acceptedHits < 0 || acceptedHits > rules.requiredHits() || acceptedHits > clock.nextStrike() || clock.nextStrike() - acceptedHits > 1
                || plan.size() != rules.strikes().size() || clock.nextStrike() > plan.size()
                || clock.elapsed() > rules.durationTicks()) throw new IllegalArgumentException("雷劫存档无效");
    }
    public static TribulationAttempt create(UUID id, UUID owner, String dimension, double x, double y, double z,
            long cap, int stability, float health, TribulationRules rules) {
        var plan=rules.strikes().stream().map(strike -> new Mark(x,y,z)).toList();
        return new TribulationAttempt(2,id,owner,dimension,x,y,z,cap,stability,health,0,rules,plan,
                new Clock(Phase.PREPARING, rules.preparationTicks(), 0, 0, 0, 0, false, "准备引劫", false));
    }
    /** 一次在线且场地已加载的 tick；落雷消费与结算由所有者随后提交。 */
    public TribulationAttempt advance(boolean inside) {
        var c = clock;
        if (c.phase() == Phase.RECOVERING) {
            int left = Math.max(0, c.recovery() - 1);
            return withClock(new Clock(left == 0 ? Phase.FAILED : Phase.RECOVERING, 0, c.elapsed(), c.nextStrike(), c.outside(), left, false, c.result(), true));
        }
        if (!active()) return this;
        int outside = inside ? 0 : c.outside() + 1;
        if (c.preparation() > 0 || c.paused()) {
            int preparation = Math.max(0, c.preparation() - 1);
            return withClock(new Clock(preparation == 0 ? Phase.ACTIVE : c.phase(), preparation,
                    c.elapsed(), c.nextStrike(), outside, 0, false, preparation == 0 ? "主雷锁定自身" : "准备引劫", false));
        }
        return withClock(new Clock(Phase.ACTIVE, 0, Math.min(rules.durationTicks(), c.elapsed() + 1),
                c.nextStrike(), outside, 0, false, "渡劫中", false));
    }
    public TribulationAttempt pause() {
        if (!active()) return this;
        var c = clock;
        return withClock(new Clock(c.phase(), Math.max(c.preparation(), rules.resumeTicks()), c.elapsed(), c.nextStrike(), c.outside(), c.recovery(), true, "暂停，重新准备后继续", c.penaltyApplied()));
    }
    public boolean due() {
        return clock.phase() == Phase.ACTIVE && clock.preparation() == 0 && !clock.paused()
                && clock.nextStrike() == acceptedHits && clock.nextStrike() < plan.size() && clock.elapsed() >= rules.strikes().get(clock.nextStrike()).tick();
    }
    public TribulationAttempt consumeStrike() {
        if (!due()) throw new IllegalStateException("雷击尚未到时或已消费");
        var c = clock;
        return withClock(new Clock(c.phase(), c.preparation(), c.elapsed(), c.nextStrike() + 1,
                c.outside(), c.recovery(), c.paused(), c.result(), c.penaltyApplied()));
    }
    public TribulationAttempt acknowledgeHit(int index) {
        if(!active() || index != acceptedHits || clock.nextStrike() != index+1)
            throw new IllegalStateException("雷击没有对应的未确认消费，或已计数");
        return new TribulationAttempt(schemaVersion,id,owner,dimension,x,y,z,cultivationCap,
                requiredStability,healthBasis,acceptedHits+1,rules,plan,clock);
    }
    public boolean interruptedHit(){return active() && clock.nextStrike()>acceptedHits;}
    public boolean readyToSucceed() {
        return clock.phase() == Phase.ACTIVE && clock.preparation() == 0 && !clock.paused()
                && acceptedHits == rules.requiredHits() && clock.nextStrike() == plan.size();
    }
    public TribulationAttempt withClock(Clock next) {
        return new TribulationAttempt(schemaVersion, id, owner, dimension, x, y, z, cultivationCap,
                requiredStability, healthBasis, acceptedHits, rules, plan, next);
    }
    public TribulationAttempt withPlan(List<Mark> marks) {
        return new TribulationAttempt(schemaVersion, id, owner, dimension, x, y, z, cultivationCap,
                requiredStability, healthBasis, acceptedHits, rules, marks, clock);
    }
    public boolean active() { return clock.phase() == Phase.PREPARING || clock.phase() == Phase.ACTIVE; }
    public boolean warning(int index) {
        var strike = rules.strikes().get(index);
        return clock.phase() == Phase.ACTIVE && clock.preparation() == 0 && !clock.paused() && index >= clock.nextStrike()
                && clock.elapsed() >= strike.tick() - strike.warningTicks() && clock.elapsed() < strike.tick();
    }
    public boolean contains(double px, double py, double pz) {
        return Math.hypot(px - x, pz - z) <= rules.arenaRadius() && Math.abs(py - y) <= rules.verticalRange();
    }
    public boolean canHit(UUID target, int index, double px, double py, double pz) {
        return owner.equals(target)&&index>=0&&index<plan.size();
    }
    /** 幂等终止；只有第一次正式失败可以申请稳定度损失。 */
    public TribulationAttempt fail(String reason, boolean penalize) {
        if (!active()) return this;
        return withClock(new Clock(penalize ? Phase.RECOVERING : Phase.STOPPED, 0, clock.elapsed(),
                clock.nextStrike(), clock.outside(), penalize ? rules.recoveryTicks() : 0, false, reason, penalize));
    }
    public int failedStability(int current) { return Math.max(0, current - rules.failureStabilityLoss(requiredStability)); }
    public String encode() { return TribulationRules.JSON.toJson(this); }
    public static TribulationAttempt decode(String json) {
        if (json == null || json.length() > 65536) throw new IllegalArgumentException("雷劫存档过长");
        return Objects.requireNonNull(TribulationRules.JSON.fromJson(json, TribulationAttempt.class));
    }
}
