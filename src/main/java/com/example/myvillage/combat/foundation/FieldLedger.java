package com.example.myvillage.combat.foundation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 有界纯逻辑账簿；不保存实体、不追补脉冲、不恢复离线收益。 */
public final class FieldLedger {
    public record Key(UUID source, String kind, String effect, String dimension) {}
    private static final class Entry {
        final Key key;
        final long expires;
        final int interval, maximum;
        final boolean once;
        long nextPulse;
        final Map<UUID, Long> attempted = new HashMap<>();
        Entry(Key key, long now, int lifetime, int interval, int maximum, boolean once) {
            this.key = key; this.expires = now + lifetime; this.nextPulse = now;
            this.interval = interval; this.maximum = maximum; this.once = once;
        }
    }
    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public boolean canAdd(UUID source, long now, int maximumInstances, int maximumPerSource) {
        long total = entries.values().stream().filter(entry -> now < entry.expires).count();
        long owned = entries.values().stream().filter(entry -> now < entry.expires && entry.key.source().equals(source)).count();
        return total < maximumInstances && owned < maximumPerSource;
    }
    public void add(long id, Key key, long now, int lifetime, int interval, int maximum, boolean once) {
        if (lifetime < 1 || interval < 1 || maximum < 1 || now > Long.MAX_VALUE - lifetime || entries.containsKey(id))
            throw new IllegalArgumentException("领域账簿参数无效");
        entries.put(id, new Entry(key, now, lifetime, interval, maximum, once));
    }
    public boolean pulse(long id, long now) {
        var entry = entries.get(id);
        if (entry == null || now >= entry.expires || now < entry.nextPulse) return false;
        entry.nextPulse = now + entry.interval;
        return true;
    }
    /** 尝试前入账，免控/零治疗也不能在同一领域反复触发。 */
    public boolean claim(long id, UUID target, long now) {
        var entry = entries.get(id);
        if (entry == null || now >= entry.expires) return false;
        Long own = entry.attempted.get(target);
        if (own != null && (entry.once || now - own < entry.interval)) return false;
        if (own == null && entry.attempted.size() >= entry.maximum) return false;
        for (var other : entries.values()) {
            if (other == entry || now >= other.expires || !entry.key.equals(other.key)) continue;
            var previous = other.attempted.get(target);
            if (previous != null && (entry.once || other.once || now - previous < Math.max(entry.interval, other.interval))) {
                // 将已有回执传递给重叠实例；旧实例到期后也不会免费刷新同一目标。
                entry.attempted.put(target, previous);
                return false;
            }
        }
        entry.attempted.put(target, now);
        return true;
    }
    public void remove(long id) { entries.remove(id); }
    public int trackedTargets(long id) {
        var entry = entries.get(id);
        return entry == null ? 0 : entry.attempted.size();
    }
    public void forget(UUID entity) {
        entries.values().removeIf(entry -> entry.key.source().equals(entity));
        // 目标死亡、卸载、退出或跨维度不释放生命周期名额，也不重置一次性效果。
        // 回执仅含 UUID，仍受每实例 maximum 约束，随所属领域一起回收。
    }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
