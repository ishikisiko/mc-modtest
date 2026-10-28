package com.example.myvillage.worldplan.travel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread admission only. Full capacity rejects immediately; there is no waiting queue. */
public final class AtlasCityTravelLimiter {
    public enum Decision { ACCEPTED, IN_FLIGHT, RATE_LIMITED, FULL }
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID,Long> nextAttempt = new HashMap<>(), nextFeedback = new HashMap<>();

    public Decision admit(UUID id, long tick, AtlasCityTravelRules rules) {
        boolean busy = active.contains(id);
        if (tick < nextAttempt.getOrDefault(id,Long.MIN_VALUE))
            return busy ? Decision.IN_FLIGHT : Decision.RATE_LIMITED;
        nextAttempt.put(id,tick+rules.requestCooldownTicks());
        if (busy) return Decision.IN_FLIGHT;
        if (active.size() >= rules.maxConcurrentRequests()) return Decision.FULL;
        active.add(id);
        return Decision.ACCEPTED;
    }
    public boolean feedback(UUID id, long tick, AtlasCityTravelRules rules) {
        if (tick < nextFeedback.getOrDefault(id,Long.MIN_VALUE)) return false;
        nextFeedback.put(id,tick+rules.feedbackIntervalTicks());
        return true;
    }
    public void release(UUID id) { active.remove(id); }
    public void forget(UUID id) { release(id); nextAttempt.remove(id); nextFeedback.remove(id); }
    public void prune(long tick) {
        nextAttempt.entrySet().removeIf(e -> e.getValue() <= tick && !active.contains(e.getKey()));
        nextFeedback.entrySet().removeIf(e -> e.getValue() <= tick && !active.contains(e.getKey()));
    }
    public void clear() { active.clear(); nextAttempt.clear(); nextFeedback.clear(); }
    public int activeCount() { return active.size(); }
}
