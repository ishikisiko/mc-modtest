package com.example.myvillage.arrival;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Per-session server state. Player entity replacement does not reset this UUID-owned tracker. */
public final class ArrivalTracker {
    public record Update(ArrivalLocation location, boolean showRegion, boolean showTown) { }
    private final ArrivalRules rules;
    private final Map<String, Long> announced = new HashMap<>();
    private ArrivalLocation current = ArrivalLocation.EMPTY;
    private ArrivalLocation candidate;
    private long candidateSince;

    public ArrivalTracker(ArrivalRules rules) { this.rules = rules; }
    public ArrivalLocation current() { return current; }

    public Optional<Update> observe(ArrivalLocation location, long tick) {
        if (!location.equals(candidate)) { candidate = location; candidateSince = tick; }
        if (location.equals(current)) return Optional.empty();
        boolean leaving = !current.regionId().isEmpty() && location.regionId().isEmpty()
                || !current.townId().isEmpty() && location.townId().isEmpty()
                    && current.regionId().equals(location.regionId());
        if (tick - candidateSince < (leaving ? rules.exitGraceTicks() : rules.settleTicks()))
            return Optional.empty();
        var previous = current;
        current = location;
        announced.entrySet().removeIf(e -> tick - e.getValue() >= rules.repeatCooldownTicks());
        boolean region = !location.regionId().isEmpty() && !location.regionId().equals(previous.regionId())
                && eligible("region/" + location.regionId(), tick);
        boolean town = !location.townId().isEmpty() && !location.townId().equals(previous.townId())
                && eligible("town/" + location.townId(), tick);
        return Optional.of(new Update(location, region, town));
    }

    private boolean eligible(String id, long tick) {
        if (announced.containsKey(id)) return false;
        announced.put(id, tick);
        return true;
    }
}
