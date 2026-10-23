package com.example.myvillage.combat.foundation;

/** 每玩家固定窗口限额；预留全部余量阻止治疗事件中的重入吸血。 */
public final class HealingWindow {
    private long window = -1;
    private double budget;
    private double spent;
    private Reservation pending;

    public Reservation reserve(long now, double maximumHealth, double capFraction, int windowTicks) {
        if (now < 0 || windowTicks <= 0 || !Double.isFinite(maximumHealth) || maximumHealth <= 0
                || !Double.isFinite(capFraction) || capFraction <= 0 || capFraction > 1 || pending != null) return null;
        long current = now / windowTicks;
        if (current < window) return null;
        double cap = Math.min(Float.MAX_VALUE, maximumHealth * capFraction);
        if (current > window) {
            window = current;
            budget = cap;
            spent = 0;
        } else {
            // 同窗扩容不补额度，缩容立即降低额度，避免临时生命上限切换刷治疗。
            budget = Math.min(budget, cap);
        }
        double remaining = Math.max(0, budget - spent);
        if (remaining <= 0) return null;
        pending = new Reservation(remaining);
        return pending;
    }

    /** 只消费实得生命；取消、满血或重伤不消耗未产生的收益。令牌只能结算一次。 */
    public boolean settle(Reservation reservation, double actualHealing) {
        if (reservation == null || reservation != pending) return false;
        double actual = Double.isFinite(actualHealing) ? Math.clamp(actualHealing, 0, reservation.amount()) : 0;
        spent = Math.min(budget, spent + actual);
        pending = null;
        return true;
    }

    public boolean expired(long now, int windowTicks) {
        return pending == null && windowTicks > 0 && now >= 0 && (window < 0 || now / windowTicks > window);
    }

    public double spent() { return spent; }
    public record Reservation(double amount) { }
}
