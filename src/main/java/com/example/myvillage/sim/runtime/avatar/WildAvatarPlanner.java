package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sim.PersonView;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.wilderness.WildernessRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure wilderness projection choices. Every output retains an existing ledger person identity. */
public final class WildAvatarPlanner {
    private WildAvatarPlanner() { }
    public record Column(int x, int z) { }
    public static boolean eligible(PersonView p, String region, WildernessRules.RealmRange range) {
        if (!p.alive() || p.sectId() != -1 || !p.rank().equals("rogue") || p.status().equals("secluded")
                || !p.regionId().equals(region)) return false;
        try { return range.contains(new RealmKey(p.realmId(), p.stage())); }
        catch (IllegalArgumentException ex) { return false; }
    }
    /** Stable hash priority, then id. Duplicated input records cannot create a second candidate. */
    public static List<PersonView> candidates(List<PersonView> living, String region, WildernessRules.RealmRange range, long seed) {
        Set<Integer> seen = new HashSet<>();
        List<PersonView> out = new ArrayList<>();
        for (PersonView p : living) if (eligible(p, region, range) && seen.add(p.id())) out.add(p);
        out.sort(Comparator.<PersonView, Long>comparing(p -> WildernessRules.mix(seed ^ p.id() ^ region.hashCode()),
                Long::compareUnsigned).thenComparingInt(PersonView::id));
        return List.copyOf(out);
    }
    /** Shared map occupancy and caps are inputs; selection cannot exceed any remaining capacity. */
    public static List<PersonView> select(List<PersonView> candidates, Collection<Integer> represented,
                                          int inRegion, int wildTotal, int sharedTotal,
                                          int maxRegion, int maxWild, int maxShared) {
        return select(candidates, represented, inRegion, wildTotal, sharedTotal, maxRegion, maxWild, maxShared, 0);
    }
    /** Rotate only unrepresented candidates. Existing avatars never move or lose their slots to a retry. */
    public static List<PersonView> select(List<PersonView> candidates, Collection<Integer> represented,
                                          int inRegion, int wildTotal, int sharedTotal,
                                          int maxRegion, int maxWild, int maxShared, int retryCursor) {
        int available = Math.max(0, Math.min(maxRegion - inRegion, Math.min(maxWild - wildTotal, maxShared - sharedTotal)));
        List<PersonView> out = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>(represented);
        List<PersonView> unrepresented = new ArrayList<>();
        for (PersonView p : candidates) if (occupied.add(p.id())) unrepresented.add(p);
        if (!unrepresented.isEmpty()) {
            int start = Math.floorMod(retryCursor, unrepresented.size());
            for (int offset = 0; offset < Math.min(available, unrepresented.size()); offset++) {
                out.add(unrepresented.get((start + offset) % unrepresented.size()));
            }
        }
        return List.copyOf(out);
    }
    /** Stable columns relative to a player's quantized chunk anchor; no settlement-time input. */
    public static List<Column> columns(long seed, int person, int anchorX, int anchorZ, WildernessRules.Rogues settings) {
        List<Column> out = new ArrayList<>();
        int radius = settings.spawnRadius(), side = 2 * radius + 1;
        for (int probe = 0; probe < settings.probesPerPerson(); probe++) {
            long h = WildernessRules.mix(seed ^ WildernessRules.mix(person) ^ WildernessRules.mix(((long) anchorX << 32)
                    ^ (anchorZ & 0xffffffffL)) ^ WildernessRules.mix(probe));
            int x = (int) Math.floorMod(h, (long) side) - radius;
            int z = (int) Math.floorMod(WildernessRules.mix(h), (long) side) - radius;
            int distance = x * x + z * z;
            if (distance >= settings.minDistance() * settings.minDistance() && distance <= radius * radius) {
                Column c = new Column(anchorX + x, anchorZ + z);
                if (!out.contains(c)) out.add(c);
            }
        }
        return List.copyOf(out);
    }
}
