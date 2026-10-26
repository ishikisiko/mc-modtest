package com.example.myvillage.combat.foundation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** 单个目标的纯逻辑账簿；不持有实体，不保存，不负责减伤或霸体。 */
public final class ShieldLedger {
    public record Entry(ResourceLocation source, float amount, long expiresAt) { }
    public record Consumption(ResourceLocation source, float blocked, float remaining, boolean broken) { }
    public record Absorption(float damage, float blocked, List<Consumption> consumed) {
        public Absorption { consumed = List.copyOf(consumed); }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::expiresAt)
            .thenComparing(entry -> entry.source().toString());
    private final ShieldRules rules;
    private final Map<ResourceLocation, Entry> sources = new HashMap<>();

    public ShieldLedger(ShieldRules rules) { this.rules = Objects.requireNonNull(rules); }

    /** 同源替换余额和期限；不同来源不互相驱逐，超额请求截至可用容量。 */
    public boolean grant(ResourceLocation source, float amount, int durationTicks, long now) {
        if (source == null || !Float.isFinite(amount) || amount <= 0 || durationTicks <= 0 || now < 0)
            return false;
        int duration = Math.min(durationTicks, rules.maxDurationTicks());
        if (now > Long.MAX_VALUE - duration) return false;
        expire(now);
        Entry previous = sources.get(source);
        if (previous == null && sources.size() >= rules.maxSources()) return false;
        double others = sum() - (previous == null ? 0 : previous.amount());
        double available = Math.max(0, (double) rules.maxTotal() - others);
        float granted = boundedFloat(Math.min(Math.min(amount, rules.maxPerSource()), available));
        if (granted <= 0) return false;
        sources.put(source, new Entry(source, granted, now + duration));
        return true;
    }

    /** Plan on a copy before resource payment; failed payment preserves all balances and expiries. */
    public boolean grantPaid(ResourceLocation source, float amount, int durationTicks, long now,
            java.util.function.BooleanSupplier payment) {
        if (payment == null || now < 0) return false;
        ShieldLedger prepared = new ShieldLedger(rules);
        for (var entry : sources.values()) if (entry.expiresAt() > now)
            if (!prepared.grant(entry.source(), entry.amount(), (int) (entry.expiresAt() - now), now)) return false;
        if (!prepared.grant(source, amount, durationTicks, now) || !payment.getAsBoolean()) return false;
        sources.clear(); sources.putAll(prepared.sources); return true;
    }

    public boolean removeSource(ResourceLocation source, long now) {
        expire(now);
        return sources.remove(source) != null;
    }

    public float total(long now) {
        expire(now);
        return (float) sum();
    }

    /** 排序后的只读副本，可用于服务器诊断，不会暴露可修改状态。 */
    public List<Entry> entries(long now) {
        expire(now);
        return sources.values().stream().sorted(ORDER).toList();
    }

    /** 输入已经通过原版和类型减免；这里只做扣除，不调用 hurt 或任何减伤函数。 */
    public Absorption absorb(float damage, long now, boolean bypass) {
        expire(now);
        if (bypass || !Float.isFinite(damage) || damage <= 0 || sources.isEmpty())
            return new Absorption(damage, 0, List.of());
        float residual = damage;
        List<Consumption> consumed = new ArrayList<>();
        for (Entry entry : entries(now)) {
            if (residual <= 0) break;
            float blocked = Math.min(entry.amount(), residual);
            float remaining = Math.max(0, entry.amount() - blocked);
            // 大数减去极小数可能不改变 float；此时不能凭空消费一个护盾。
            float nextResidual = Math.max(0, residual - blocked);
            if (nextResidual == residual) continue;
            if (remaining == 0) sources.remove(entry.source());
            else sources.put(entry.source(), new Entry(entry.source(), remaining, entry.expiresAt()));
            consumed.add(new Consumption(entry.source(), blocked, remaining, remaining == 0));
            residual = nextResidual;
        }
        return new Absorption(residual, Math.max(0, damage - residual), consumed);
    }

    public void expire(long now) { sources.values().removeIf(entry -> now >= entry.expiresAt()); }
    public boolean isEmpty() { return sources.isEmpty(); }
    public void clear() { sources.clear(); }

    private double sum() {
        double amount = 0;
        for (Entry entry : sources.values()) amount += entry.amount();
        return amount;
    }

    /** 向下取可表示的值，保证所有来源的实际总量不越过作者配置。 */
    private static float boundedFloat(double value) {
        float result = (float) value;
        return result > value ? Math.nextDown(result) : result;
    }
}
