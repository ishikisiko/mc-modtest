package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimObserver;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.Tombstone;
import com.example.myvillage.sim.model.WorldState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The engine's working set for one world: the ledger, the data, the region graph, the chronicle and
 * derived indices. Indices are pure functions of the ledger (rebuilt on load and when marked dirty),
 * so they never make a restored world diverge.
 */
public final class SimContext {
    public final WorldState state;
    public final SimData data;
    public final Rules rules;
    public final RealmTable realms;
    public final RegionGraph graph;
    public final Chronicle chronicle;
    /** Days per year for the step in progress. */
    public int dpy;

    private final Map<String, GenRegion> regionById = new LinkedHashMap<>();
    /** Regions reachable over one 连 edge (a walled region only through its pass), in id order. */
    private final Map<String, List<String>> neighbours = new LinkedHashMap<>();
    private Map<String, List<Person>> peopleByRegion;
    private Map<Integer, List<Person>> membersBySect;
    private Set<String> usedNames;
    private Set<String> usedSectNames;
    private Set<String> usedDaoNames;

    public SimContext(WorldState state, SimData data, RegionGraph graph, int dpy) {
        this.state = state;
        this.data = data;
        this.rules = data.rules();
        this.realms = data.realms();
        this.graph = graph;
        this.dpy = dpy;
        for (GenRegion r : graph.regions()) {
            regionById.put(r.id(), r);
            neighbours.put(r.id(), new ArrayList<>());
        }
        for (var e : graph.edges()) {
            if (e.type().equals(com.example.myvillage.region.runtime.RegionContract.EDGE_LIAN)) {
                neighbours.get(e.a()).add(e.b());
                neighbours.get(e.b()).add(e.a());
            }
        }
        for (List<String> list : neighbours.values()) {
            list.sort(String::compareTo);
        }
        this.chronicle = new Chronicle(state, TextKeys.eventKeys(data), TextKeys.families(data),
                rules.chronicle().minorPerPerson());
    }

    public void setObserver(SimObserver observer) {
        chronicle.setObserver(observer);
    }

    public long day() {
        return state.day;
    }

    public SimRng rng(long subject, int purpose) {
        return SimRng.at(state.seed, state.day, subject, purpose);
    }

    public SimRng rng(long subject, int purpose, long salt) {
        return SimRng.at(state.seed, state.day, subject, purpose, salt);
    }

    public boolean newYear() {
        return state.day % dpy == 0;
    }

    // ------------------------------------------------------------------ people

    public RealmTable.Realm realm(Person p) {
        return realms.get(p.realm);
    }

    public double ageYears(Person p) {
        return (state.day - p.birthDay) / (double) dpy;
    }

    public double lifespanYears(Person p) {
        return realm(p).lifespanYears() + p.bonusLifespanYears;
    }

    public Rules.RootGrade rootGrade(Person p) {
        return Roots.grade(rules.roots(), p.root);
    }

    public ContentTables.Technique technique(Person p) {
        return data.technique(p.techniqueId);
    }

    /** Ordering key for strength: realm, then stage, then progress. */
    public static double standing(Person p) {
        return p.realm * 1000.0 + p.stage * 10.0 + Math.min(p.progress, 9.99) / 1000.0;
    }

    /** Name of a living or dead person; "" for an unknown id. */
    public String nameOf(int personId) {
        Person p = state.persons.get(personId);
        if (p != null) {
            return p.name();
        }
        Tombstone t = state.tombstones.get(personId);
        return t == null ? "" : t.name;
    }

    /** Daoist title (name + suffix of the highest titled realm reached), or "". */
    public String title(Person p) {
        if (p.daoName.isEmpty()) {
            return "";
        }
        String suffix = "";
        for (int r = 0; r <= p.realm; r++) {
            if (realms.get(r).titleSuffix() != null) {
                suffix = realms.get(r).titleSuffix();
            }
        }
        return p.daoName + suffix;
    }

    // ------------------------------------------------------------------ sects

    public Sect sect(int id) {
        return state.sects.get(id);
    }

    public String sectName(int id) {
        Sect s = state.sects.get(id);
        return s == null ? "" : s.name;
    }

    /** Living members of a sect in id order (index rebuilt when membership changed). */
    public List<Person> members(int sectId) {
        if (membersBySect == null) {
            membersBySect = new HashMap<>();
            for (Person p : state.persons.values()) {
                if (p.sectId >= 0) {
                    membersBySect.computeIfAbsent(p.sectId, k -> new ArrayList<>()).add(p);
                }
            }
        }
        return membersBySect.getOrDefault(sectId, List.of());
    }

    /** Call after any change to who is alive or who belongs to which sect. */
    public void membershipChanged() {
        membersBySect = null;
    }

    public List<Sect> activeSects() {
        List<Sect> out = new ArrayList<>();
        for (Sect s : state.sects.values()) {
            if (s.active()) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ regions

    public GenRegion region(String id) {
        GenRegion r = regionById.get(id);
        if (r == null) {
            throw new IllegalArgumentException("unknown region " + id);
        }
        return r;
    }

    public boolean hasRegion(String id) {
        return regionById.containsKey(id);
    }

    public String regionName(String id) {
        GenRegion r = regionById.get(id);
        return r == null ? "" : r.displayName();
    }

    public static double qiMid(GenRegion r) {
        return (r.qi().lo() + r.qi().hi()) / 2.0;
    }

    public static double dangerMid(GenRegion r) {
        return (r.danger().lo() + r.danger().hi()) / 2.0;
    }

    public static boolean admitsSects(GenRegion r) {
        return r.admittedSubjects().contains("sect");
    }

    public List<String> neighbours(String regionId) {
        return neighbours.getOrDefault(regionId, List.of());
    }

    /** The next region on a shortest 连 path from {@code from} to {@code to}; {@code from} if unreachable. */
    public String nextStep(String from, String to) {
        if (from.equals(to)) {
            return from;
        }
        Map<String, String> parent = new LinkedHashMap<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        parent.put(from, from);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            for (String v : neighbours(u)) {
                if (!parent.containsKey(v)) {
                    parent.put(v, u);
                    if (v.equals(to)) {
                        String step = v;
                        while (!parent.get(step).equals(from)) {
                            step = parent.get(step);
                        }
                        return step;
                    }
                    queue.add(v);
                }
            }
        }
        return from;
    }

    /** Living people in a region as of the start of the day, in id order (may include the newly dead). */
    public List<Person> peopleIn(String regionId) {
        if (peopleByRegion == null) {
            peopleByRegion = new HashMap<>();
            for (Person p : state.persons.values()) {
                peopleByRegion.computeIfAbsent(p.regionId, k -> new ArrayList<>()).add(p);
            }
        }
        return peopleByRegion.getOrDefault(regionId, List.of());
    }

    /** A kept chronicle event by id (binary search), or null when pruned or unknown. */
    public com.example.myvillage.sim.SimEvent findEvent(long eventId) {
        List<com.example.myvillage.sim.SimEvent> all = state.chronicle;
        int lo = 0;
        int hi = all.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long id = all.get(mid).id();
            if (id == eventId) {
                return all.get(mid);
            }
            if (id < eventId) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null;
    }

    /** Called at the start of each day. */
    public void newDay() {
        peopleByRegion = null;
    }

    public boolean alive(Person p) {
        return state.persons.get(p.id) == p;
    }

    // ------------------------------------------------------------------ names

    public Set<String> usedNames() {
        if (usedNames == null) {
            usedNames = new HashSet<>();
            for (Person p : state.persons.values()) {
                usedNames.add(p.name());
            }
            for (Tombstone t : state.tombstones.values()) {
                usedNames.add(t.name);
            }
        }
        return usedNames;
    }

    public Set<String> usedSectNames() {
        if (usedSectNames == null) {
            usedSectNames = new HashSet<>();
            for (Sect s : state.sects.values()) {
                usedSectNames.add(s.name);
            }
        }
        return usedSectNames;
    }

    public Set<String> usedDaoNames() {
        if (usedDaoNames == null) {
            usedDaoNames = new HashSet<>();
            for (Person p : state.persons.values()) {
                if (!p.daoName.isEmpty()) {
                    usedDaoNames.add(p.daoName);
                }
            }
            for (Tombstone t : state.tombstones.values()) {
                if (!t.daoName.isEmpty()) {
                    usedDaoNames.add(t.daoName);
                }
            }
        }
        return usedDaoNames;
    }
}
