package com.example.myvillage.combat.runtime;

/** One accepted action contributes once, even with several targets, hits or an intervening spend. */
public final class DisciplinePool {
    private int stacks;
    private long lastAction = Long.MIN_VALUE, expires = Long.MIN_VALUE;
    public int stacks(long now) {
        if (now >= expires) stacks = 0;
        return stacks;
    }
    public boolean gain(long action, long now, DisciplineRules.Pool rules) {
        if (action <= lastAction) return false;
        lastAction = action;
        gain(now, rules);
        return true;
    }
    void gain(long now, DisciplineRules.Pool rules) {
        stacks(now);
        stacks = Math.min(rules.maximum(), stacks + 1);
        expires = now + rules.timeoutTicks();
    }
    public double consume(long now, DisciplineRules.Pool rules) {
        return 1 + consumeStacks(now) * rules.bonus();
    }
    int consumeStacks(long now) {
        int spent = stacks(now);
        stacks = 0;
        return spent;
    }
}
