package com.example.myvillage.arrival;

import java.util.ArrayDeque;
import java.util.Optional;

/** A maximum of two current-destination cards; stale destinations are removed immediately. */
public final class ArrivalSequence {
    public enum Kind { REGION, TOWN }
    public record Card(Kind kind, ArrivalLocation location) {
        public String name() { return kind == Kind.REGION ? location.regionName() : location.townName(); }
        public boolean matches(ArrivalLocation next) {
            return kind == Kind.REGION ? location.regionId().equals(next.regionId())
                    : location.regionId().equals(next.regionId()) && location.townId().equals(next.townId());
        }
    }
    private final ArrivalRules rules;
    private final ArrayDeque<Card> pending = new ArrayDeque<>();
    private Card active;
    private int age, gap;

    public ArrivalSequence(ArrivalRules rules) { this.rules = rules; }
    public Optional<Card> current() { return Optional.ofNullable(active); }
    public int age() { return age; }
    public int duration(Card card) { return card.kind() == Kind.REGION ? rules.regionDurationTicks() : rules.townDurationTicks(); }
    public void clear() { active = null; pending.clear(); age = 0; gap = 0; }

    public void accept(ArrivalLocation next, boolean region, boolean town) {
        pending.removeIf(card -> !card.matches(next));
        if (active != null && !active.matches(next)) { active = null; age = 0; gap = 0; }
        if (region) add(new Card(Kind.REGION, next));
        if (town) add(new Card(Kind.TOWN, next));
    }

    private void add(Card card) {
        if (!card.equals(active) && !pending.contains(card)) pending.addLast(card);
    }

    public void tick() {
        if (active != null && ++age >= duration(active)) { active = null; age = 0; gap = rules.gapTicks(); }
        if (active == null) {
            if (gap > 0) { gap--; return; }
            active = pending.pollFirst(); age = 0;
        }
    }

    public float opacity(float partial) {
        if (active == null) return 0;
        float t = age + Math.clamp(partial, 0, 1);
        float amount = Math.min(t / rules.fadeInTicks(), (duration(active) - t) / rules.fadeOutTicks());
        float v = Math.clamp(amount, 0, 1);
        return v * v * (3 - 2 * v);
    }
}
