package com.example.myvillage.combat.foundation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.function.Predicate;
import java.util.UUID;

/** 每个目标的有界暂态账簿，不持有实体或存档引用，调用方提供服务器时间。 */
public final class ControlState {
    public enum Kind { ROOT, SLOW, POISE }
    private record Key(Kind kind, UUID caster, String effect) { }
    private record Entry(long until, double fraction) { }
    private final Map<Key, Entry> entries = new HashMap<>();
    private long lastRoot = Long.MIN_VALUE;
    private int rootStacks;

    public int rootStacks(long now, int repeatWindow) {
        return lastRoot != Long.MIN_VALUE && now >= lastRoot && now - lastRoot <= repeatWindow ? rootStacks : 0;
    }

    public void recordRoot(long now, int repeatWindow) {
        rootStacks = Math.min(Integer.MAX_VALUE - 1, rootStacks(now, repeatWindow)) + 1;
        lastRoot = now;
    }

    public boolean put(Kind kind, UUID caster, String effect, long now, int ticks, double fraction, int maximumSources) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(caster);
        Objects.requireNonNull(effect);
        expire(now);
        if (effect.isBlank() || ticks <= 0 || maximumSources <= 0 || !Double.isFinite(fraction)
                || fraction < 0 || fraction > 1 || now > Long.MAX_VALUE - ticks) return false;
        Key key = new Key(kind, caster, effect);
        Entry previous = entries.get(key);
        if (previous == null && entries.size() >= maximumSources) return false;
        entries.put(key, new Entry(Math.max(now + ticks, previous == null ? Long.MIN_VALUE : previous.until), fraction));
        return true;
    }

    public boolean has(Kind kind, long now) {
        return entries.entrySet().stream().anyMatch(e -> e.getKey().kind == kind && e.getValue().until > now);
    }

    public double strongestSlow(long now) {
        return entries.entrySet().stream().filter(e -> e.getKey().kind == Kind.SLOW && e.getValue().until > now)
                .mapToDouble(e -> e.getValue().fraction).max().orElse(0);
    }

    /** 净化保留霸体和重复控制衰减历史，避免净化后立刻重置抗连控。 */
    public int cleanse() {
        int before = entries.size();
        entries.keySet().removeIf(key -> key.kind != Kind.POISE);
        return before - entries.size();
    }

    public int forgetCaster(UUID caster) {
        int before = entries.size();
        entries.keySet().removeIf(key -> key.caster.equals(caster));
        return before - entries.size();
    }

    public int revoke(UUID caster, String effect) {
        int before = entries.size();
        entries.keySet().removeIf(key -> key.caster.equals(caster) && key.effect.equals(effect));
        return before - entries.size();
    }

    public int remaining(Kind kind, long now) {
        return (int) Math.min(Integer.MAX_VALUE, entries.entrySet().stream()
                .filter(e -> e.getKey().kind == kind && e.getValue().until > now)
                .mapToLong(e -> e.getValue().until - now).max().orElse(0));
    }

    public record SourceSnapshot(Kind kind, UUID caster, String effect, int remainingTicks, double fraction) { }
    public List<SourceSnapshot> snapshot(long now) {
        return entries.entrySet().stream().filter(e -> e.getValue().until > now)
                .map(e -> new SourceSnapshot(e.getKey().kind, e.getKey().caster, e.getKey().effect,
                        (int) Math.min(Integer.MAX_VALUE, e.getValue().until - now), e.getValue().fraction))
                .toList();
    }

    public void pruneHostileSources(Predicate<UUID> permitted) {
        entries.keySet().removeIf(key -> key.kind != Kind.POISE && !permitted.test(key.caster));
    }

    public void expire(long now) { entries.values().removeIf(entry -> entry.until <= now); }
    public int size() { return entries.size(); }
    public boolean idle(long now, int repeatWindow) {
        expire(now);
        return entries.isEmpty() && (lastRoot == Long.MIN_VALUE || now - lastRoot > repeatWindow);
    }
}
