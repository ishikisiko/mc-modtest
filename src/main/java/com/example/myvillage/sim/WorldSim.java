package com.example.myvillage.sim;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.SimDataLoader;
import com.example.myvillage.sim.engine.Engine;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.SectRelation;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.Tombstone;
import com.example.myvillage.sim.model.WorldState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The world ledger (命簿): the only authority on who lives, where sects stand and what happened.
 * This facade and the view records are all the runtime and tools use (design §2.1). Pure: no
 * Minecraft types; deterministic in (seed, graph, data, tier, daysPerYear).
 */
public final class WorldSim {
    private final SimContext ctx;
    private SimObserver observer;

    private WorldSim(SimContext ctx, SimObserver observer) {
        this.ctx = ctx;
        this.observer = observer;
        ctx.setObserver(observer);
    }

    /** Loads rules/realms/encounters/names/techniques/lore through an opener. */
    public static SimData loadData(SimData.ResourceOpener opener) {
        return SimDataLoader.load(opener);
    }

    /** Creates the world and runs the prehistory. Deterministic in (seed, graph, data, tier, daysPerYear). */
    public static WorldSim genesis(long seed, RegionGraph graph, SimData data, String tierId, int daysPerYear) {
        return genesis(seed, graph, data, tierId, daysPerYear, null);
    }

    /**
     * As {@link #genesis(long, RegionGraph, SimData, String, int)}, with an observer that sees every
     * event (prehistory included) and the world at the end of day 0 and of every settled day.
     */
    public static WorldSim genesis(long seed, RegionGraph graph, SimData data, String tierId, int daysPerYear,
                                   SimObserver observer) {
        requireDaysPerYear(daysPerYear);
        data.rules().tier(tierId);
        WorldState state = new WorldState();
        state.seed = seed;
        state.tierId = tierId;
        state.genesisDaysPerYear = daysPerYear;
        state.prehistoryDays = (long) data.rules().time().prehistoryYears() * daysPerYear;
        WorldSim sim = new WorldSim(new SimContext(state, data, graph, daysPerYear), observer);
        Engine.genesis(sim.ctx);
        if (observer != null) {
            observer.onDayEnd(sim);
        }
        for (long d = 0; d < state.prehistoryDays; d++) {
            sim.step(daysPerYear);
        }
        return sim;
    }

    /** Restores a saved world. Throws SimFormatException on a bad or newer-format payload. */
    public static WorldSim fromBytes(byte[] saved, RegionGraph graph, SimData data) {
        WorldState state = StateCodec.fromBytes(saved, data.realms());
        if (!data.rules().tiers().containsKey(state.tierId)) {
            throw new SimFormatException("tier \"" + state.tierId + "\" is not in the loaded rules");
        }
        SimContext ctx = new SimContext(state, data, graph, Math.max(1, state.genesisDaysPerYear));
        for (Person p : state.persons.values()) {
            requireRegion(ctx, p.regionId, "person " + p.id);
        }
        for (Sect s : state.sects.values()) {
            requireRegion(ctx, s.homeRegionId, "sect " + s.id);
        }
        return new WorldSim(ctx, null);
    }

    private static void requireRegion(SimContext ctx, String regionId, String owner) {
        if (!ctx.hasRegion(regionId)) {
            throw new SimFormatException(owner + " is in region \"" + regionId
                    + "\", which the region graph for this seed does not have");
        }
    }

    private static void requireDaysPerYear(int daysPerYear) {
        if (daysPerYear <= 0) {
            throw new IllegalArgumentException("daysPerYear must be positive, got " + daysPerYear);
        }
    }

    /** Canonical: equal states give equal bytes. */
    public byte[] toBytes() {
        return StateCodec.toBytes(ctx.state, ctx.realms);
    }

    /** Advances exactly one sim day; returns the events of that day in order. */
    public List<SimEvent> step(int daysPerYear) {
        requireDaysPerYear(daysPerYear);
        ctx.dpy = daysPerYear;
        List<SimEvent> events = Engine.step(ctx);
        if (observer != null) {
            observer.onDayEnd(this);
        }
        return events;
    }

    /** Replaces the observer (null to remove). Not saved. */
    public void setObserver(SimObserver observer) {
        this.observer = observer;
        ctx.setObserver(observer);
    }

    /** The settlement scheduler; its state is saved with the world. */
    public SettlementScheduler scheduler() {
        return ctx.state.scheduler;
    }

    /** Sim days since genesis (prehistory included). */
    public long day() {
        return ctx.state.day;
    }

    public long prehistoryDays() {
        return ctx.state.prehistoryDays;
    }

    public long seed() {
        return ctx.state.seed;
    }

    public SimDate date(int daysPerYear) {
        requireDaysPerYear(daysPerYear);
        return SimDate.of(ctx.state.day, ctx.state.prehistoryDays, daysPerYear);
    }

    public String tierId() {
        return ctx.state.tierId;
    }

    // ------------------------------------------------------------------ views

    public Overview overview(int daysPerYear) {
        Map<String, Integer> byRealm = new LinkedHashMap<>();
        for (var realm : ctx.realms.realms()) {
            byRealm.put(realm.id(), 0);
        }
        for (Person p : ctx.state.persons.values()) {
            byRealm.merge(ctx.realm(p).id(), 1, Integer::sum);
        }
        int active = 0;
        int destroyed = 0;
        for (Sect s : ctx.state.sects.values()) {
            if (s.active()) {
                active++;
            } else {
                destroyed++;
            }
        }
        List<Person> top = new ArrayList<>(ctx.state.persons.values());
        top.sort(Comparator.comparingDouble(SimContext::standing).reversed().thenComparingInt(p -> p.id));
        List<Integer> topIds = new ArrayList<>();
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            topIds.add(top.get(i).id);
        }
        return new Overview(ctx.state.day, date(daysPerYear), ctx.state.tierId, ctx.state.persons.size(),
                ctx.rules.tier(ctx.state.tierId).population(), byRealm, active, destroyed,
                ctx.state.tombstones.size(), ctx.state.nextEventId - 1, topIds);
    }

    public List<SectView> sects(boolean includeDestroyed) {
        List<SectView> out = new ArrayList<>();
        for (Sect s : ctx.state.sects.values()) {
            if (includeDestroyed || s.active()) {
                out.add(sectView(s));
            }
        }
        return out;
    }

    public Optional<SectView> sect(int sectId) {
        Sect s = ctx.state.sects.get(sectId);
        return s == null ? Optional.empty() : Optional.of(sectView(s));
    }

    private SectView sectView(Sect s) {
        List<Person> members = ctx.members(s.id);
        Person top = null;
        for (Person p : members) {
            if (top == null || SimContext.standing(p) > SimContext.standing(top)) {
                top = p;
            }
        }
        List<SectView.Relation> relations = new ArrayList<>();
        for (SectRelation r : s.relations.values()) {
            relations.add(new SectView.Relation(r.other, r.value, r.state, r.causeEventId));
        }
        ContentTables.Technique sig = ctx.data.technique(s.signatureTechniqueId);
        return new SectView(s.id, s.name, s.homeRegionId, s.gateX, s.gateZ, s.gateRealized, s.founderId,
                ctx.nameOf(s.founderId), s.foundedDay, s.masterId, ctx.nameOf(s.masterId), members.size(),
                top == null ? "" : ctx.realm(top).id(), s.resources, s.prestige, s.signatureTechniqueId,
                sig == null ? "" : sig.name(), s.state, s.destroyedDay, s.parentSectId, List.copyOf(relations));
    }

    /** Living first, then the dead, each in id order; matches name or Daoist title. */
    public List<PersonView> findPersons(String nameFragment, int limit) {
        List<PersonView> out = new ArrayList<>();
        for (Person p : ctx.state.persons.values()) {
            if (out.size() >= limit) {
                return out;
            }
            if (p.name().contains(nameFragment) || ctx.title(p).contains(nameFragment)) {
                out.add(personView(p));
            }
        }
        for (Tombstone t : ctx.state.tombstones.values()) {
            if (out.size() >= limit) {
                return out;
            }
            if (t.name.contains(nameFragment) || t.title.contains(nameFragment)) {
                out.add(tombView(t));
            }
        }
        return out;
    }

    public Optional<PersonView> person(int personId) {
        Person p = ctx.state.persons.get(personId);
        if (p != null) {
            return Optional.of(personView(p));
        }
        Tombstone t = ctx.state.tombstones.get(personId);
        return t == null ? Optional.empty() : Optional.of(tombView(t));
    }

    /** Living members whose status is "at the sect", in id order. */
    public List<PersonView> membersAt(int sectId) {
        List<PersonView> out = new ArrayList<>();
        for (Person p : ctx.members(sectId)) {
            if (p.status.equals("at_sect")) {
                out.add(personView(p));
            }
        }
        return out;
    }

    /** The latest {@code limit} kept events of at least {@code minImportance}, oldest first. */
    public List<SimEvent> recentEvents(int minImportance, int limit) {
        List<SimEvent> out = new ArrayList<>();
        List<SimEvent> all = ctx.state.chronicle;
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            if (all.get(i).importance() >= minImportance) {
                out.add(all.get(i));
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    public RegionView region(String regionId, int daysPerYear) {
        GenRegion r = ctx.region(regionId);
        int living = 0;
        for (Person p : ctx.state.persons.values()) {
            if (p.regionId.equals(regionId)) {
                living++;
            }
        }
        List<Integer> sectIds = new ArrayList<>();
        for (Sect s : ctx.state.sects.values()) {
            if (s.active() && s.homeRegionId.equals(regionId)) {
                sectIds.add(s.id);
            }
        }
        List<SimEvent> recent = new ArrayList<>();
        List<SimEvent> all = ctx.state.chronicle;
        for (int i = all.size() - 1; i >= 0 && recent.size() < 10; i--) {
            SimEvent e = all.get(i);
            if (e.importance() >= 2 && e.regionId().equals(regionId)) {
                recent.add(e);
            }
        }
        java.util.Collections.reverse(recent);
        return new RegionView(r.id(), r.displayName(), r.tier(), r.qi().lo(), r.qi().hi(), r.danger().lo(),
                r.danger().hi(), SimContext.admitsSects(r), living, sectIds, recent);
    }

    private PersonView personView(Person p) {
        List<PersonView.Relation> relations = new ArrayList<>();
        for (Relation r : p.relations) {
            relations.add(new PersonView.Relation(r.other, r.kind, r.strength, r.causeEventId));
        }
        ContentTables.Technique t = ctx.technique(p);
        return new PersonView(p.id, p.name(), ctx.title(p), p.gender, true, p.birthDay, -1, "", -1,
                rootList(p.root), ctx.rootGrade(p).id(), ctx.realm(p).id(), p.stage, p.progress, p.sectId,
                ctx.sectName(p.sectId), p.rank, p.masterId, p.regionId, p.status, p.techniqueId,
                t == null ? "" : t.name(), p.injury, List.copyOf(relations));
    }

    private PersonView tombView(Tombstone t) {
        return new PersonView(t.id, t.name, t.title, t.gender, false, t.birthDay, t.deathDay, t.cause, t.killerId,
                List.of(), t.rootGrade, ctx.realms.get(t.realm).id(), t.stage, 0.0, t.sectId, ctx.sectName(t.sectId),
                t.rank, t.masterId, "", "dead", "", "", 0, List.of());
    }

    private static List<Integer> rootList(int[] root) {
        List<Integer> out = new ArrayList<>(root.length);
        for (int v : root) {
            out.add(v);
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ mutations

    public void markGateRealized(int sectId, boolean realized) {
        requireSect(sectId).gateRealized = realized;
    }

    /** Admin/test only; rejects a point outside any region. Moves the sect's seat with its gate. */
    public void moveGate(int sectId, int worldX, int worldZ) {
        Sect s = requireSect(sectId);
        Optional<String> region = RegionQueries.regionAt(ctx.graph, worldX, worldZ);
        if (region.isEmpty()) {
            throw new IllegalArgumentException("(" + worldX + ", " + worldZ + ") is outside every region");
        }
        String old = s.homeRegionId;
        s.gateX = worldX;
        s.gateZ = worldZ;
        s.homeRegionId = region.get();
        for (Person p : ctx.members(sectId)) {
            if (p.homeRegionId.equals(old)) {
                p.homeRegionId = s.homeRegionId;
            }
            if (p.status.equals("at_sect")) {
                p.regionId = s.homeRegionId;
            }
        }
    }

    private Sect requireSect(int sectId) {
        Sect s = ctx.state.sects.get(sectId);
        if (s == null) {
            throw new IllegalArgumentException("no sect with id " + sectId);
        }
        return s;
    }
}
