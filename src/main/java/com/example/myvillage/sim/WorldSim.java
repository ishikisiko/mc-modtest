package com.example.myvillage.sim;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.SimDataLoader;
import com.example.myvillage.sim.engine.Engine;
import com.example.myvillage.sim.engine.PlayerAffairs;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
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
import java.util.function.Predicate;

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

    /** Ids of the heritages lost with their sects and not yet rekindled, in the order they were lost. */
    public List<String> lostHeritageIds() {
        List<String> out = new ArrayList<>();
        ctx.state.lostHeritages.forEach(h -> out.add(h.heritageId()));
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
        ContentTables.Heritage heritage = ctx.data.heritage(s.heritageId);
        return new SectView(s.id, s.name, s.homeRegionId, s.gateX, s.gateZ, s.gateRealized, s.founderId,
                ctx.nameOf(s.founderId), s.foundedDay, s.masterId, ctx.nameOf(s.masterId), members.size(),
                top == null ? "" : ctx.realm(top).id(), s.resources, s.prestige, s.basicTechniqueId, s.signatureTechniqueId,
                sig == null ? "" : sig.name(), heritage == null ? "" : heritage.id(),
                heritage == null ? "" : heritage.name(), s.state, s.destroyedDay, s.parentSectId,
                List.copyOf(relations));
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
        return recentEvents(minImportance, limit, e -> true);
    }

    /**
     * The latest {@code limit} kept events of at least {@code minImportance} that pass
     * {@code filter}, oldest first.
     */
    public List<SimEvent> recentEvents(int minImportance, int limit, Predicate<SimEvent> filter) {
        List<SimEvent> out = new ArrayList<>();
        List<SimEvent> all = ctx.state.chronicle;
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            SimEvent e = all.get(i);
            if (e.importance() >= minImportance && filter.test(e)) {
                out.add(e);
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** The kept event with this id, or empty when there is none or it has been pruned. */
    public Optional<SimEvent> event(long id) {
        List<SimEvent> all = ctx.state.chronicle;
        int lo = 0;
        int hi = all.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long midId = all.get(mid).id();
            if (midId < id) {
                lo = mid + 1;
            } else if (midId > id) {
                hi = mid - 1;
            } else {
                return Optional.of(all.get(mid));
            }
        }
        return Optional.empty();
    }

    /** The sim's realm ids, weakest first. */
    public List<String> realmIds() {
        List<String> out = new ArrayList<>();
        for (var realm : ctx.realms.realms()) {
            out.add(realm.id());
        }
        return List.copyOf(out);
    }

    /** The name of a person, living or dead, or "" when the id is unknown. */
    public String nameOf(int personId) {
        return ctx.nameOf(personId);
    }

    /** The sect a person, living or dead, belongs (or last belonged) to, or -1. */
    public int sectOf(int personId) {
        Person p = ctx.state.persons.get(personId);
        if (p != null) {
            return p.sectId;
        }
        Tombstone t = ctx.state.tombstones.get(personId);
        return t == null ? -1 : t.sectId;
    }

    /** Living people now in a region, in id order. */
    public List<PersonView> livingIn(String regionId) {
        List<PersonView> out = new ArrayList<>();
        for (Person p : ctx.state.persons.values()) {
            if (p.regionId.equals(regionId)) {
                out.add(personView(p));
            }
        }
        return out;
    }

    /** True when the region graph has this region (so {@link #region} will not throw). */
    public boolean hasRegion(String regionId) {
        return ctx.hasRegion(regionId);
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
                t.rank, t.masterId, "", "dead", t.techniqueId, techniqueName(t.techniqueId), 0, List.of());
    }

    private String techniqueName(String id) {
        ContentTables.Technique t = ctx.data.technique(id);
        return t == null ? "" : t.name();
    }

    private static List<Integer> rootList(int[] root) {
        List<Integer> out = new ArrayList<>(root.length);
        for (int v : root) {
            out.add(v);
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ players (sect entry, slice 1)
    //
    // The player-membership interface. A player is never a person: players are kept in
    // WorldState.playerMembers by UUID string, are not in members()/membersAt(), and never count
    // as a sect's people. The runtime (sim.runtime.player.WorldSimPlayers) is the only caller that
    // mutates; it refreshes the qualification snapshot and marks the saved data dirty.

    /** The ledger record of a player (by UUID string), or empty when the player never joined a sect. */
    public Optional<PlayerMemberView> playerMember(String playerId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        return m == null ? Optional.empty() : Optional.of(playerMemberView(m));
    }

    /** Every player record, current members and former ones, in player-id order. */
    public List<PlayerMemberView> playerMembers() {
        List<PlayerMemberView> out = new ArrayList<>();
        for (PlayerMember m : ctx.state.playerMembers.values()) {
            out.add(playerMemberView(m));
        }
        return out;
    }

    /**
     * The sect's steward (守山执事), derived and never stored: of the members at the sect
     * ({@link #membersAt}), the lowest rank (outer, inner, elder, sect master, anything else), then
     * the lowest id. Empty when nobody is at the sect.
     */
    public Optional<PersonView> stewardOf(int sectId) {
        PersonView best = null;
        for (PersonView p : membersAt(sectId)) {
            if (best == null || stewardOrder(p.rank()) < stewardOrder(best.rank())
                    || (stewardOrder(p.rank()) == stewardOrder(best.rank()) && p.id() < best.id())) {
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int stewardOrder(String rank) {
        return switch (rank) {
            case "outer" -> 0;
            case "inner" -> 1;
            case "elder" -> 2;
            case "sect_master" -> 3;
            default -> 4;
        };
    }

    /**
     * Whether this player may join this sect now, judged from the ledger record, the sect, the
     * qualification and {@code rules.player.admission}/{@code leave}. Does not change the ledger.
     */
    public Admission admission(String playerId, int sectId, PlayerQualification q) {
        return PlayerAffairs.admission(ctx, playerId, sectId, q);
    }

    /**
     * Joins the player to the sect as an outer disciple and records a {@code player_join} event.
     * Throws IllegalArgumentException with the {@link Admission} reason when the player may not
     * join.
     *
     * <p>Called between settled days: the event is dated {@link #day()} (the day the next
     * {@link #step} settles), is in the chronicle at once ({@link #recentEvents}), and is not
     * returned again by the next {@link #step}.
     */
    public SimEvent joinSect(String playerId, String playerName, int sectId, PlayerQualification q) {
        return joinSect(playerId, playerName, sectId, q, false);
    }

    /**
     * As {@link #joinSect(String, String, int, PlayerQualification)}; {@code force} (admin commands
     * only) needs just an active sect the player is not already in (else IllegalArgumentException
     * "sect_inactive" or "already_member") and skips cooldown, standing, awakening, realm and the
     * selective bar. A forced player in another sect leaves it first with no standing penalty and no
     * {@code player_leave} event. The join standing, snapshot and {@code player_join} event apply.
     */
    public SimEvent joinSect(String playerId, String playerName, int sectId, PlayerQualification q, boolean force) {
        return PlayerAffairs.join(ctx, playerId, playerName, sectId, q, force);
    }

    /**
     * The player leaves their sect (standing penalty, rejoin cooldown); records {@code player_leave}
     * (timed like {@link #joinSect}). Throws IllegalArgumentException("not_member") when the player
     * is in no sect.
     */
    public SimEvent leaveSect(String playerId, String playerName) {
        return PlayerAffairs.leave(ctx, playerId, playerName);
    }

    /**
     * Admin: sets a member's rank ({@code outer}, {@code inner} or {@code elder}). A rise records
     * {@code player_promotion} (timed like {@link #joinSect}); empty when the rank is unchanged or
     * lowered. Throws IllegalArgumentException for an unknown rank or ("not_member") a player in no
     * sect.
     */
    public Optional<SimEvent> promotePlayer(String playerId, String playerName, String rank) {
        return PlayerAffairs.promote(ctx, playerId, playerName, rank);
    }

    // ------------------------------------------------------------------ scripture hall (sect entry, slice 2)

    /**
     * The techniques (ledger ids, paths without namespace) this player may borrow from their sect's
     * scripture hall at their rank, in chain order without duplicates: outer the heritage chain's
     * first technique (no heritage: the sect's basic technique), inner the first two (no heritage:
     * basic and signature), elder the whole chain (no heritage: basic and signature). Empty ids and
     * {@code basic_breathing} (mortal grade, no manual) are left out. Empty when the player is in
     * no sect.
     */
    public List<String> borrowable(String playerId) {
        return PlayerAffairs.borrowable(ctx, playerId);
    }

    /** Whether the player has borrowed this technique's manual (the record outlives membership). */
    public boolean hasBorrowed(String playerId, String techniqueId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        return m != null && m.borrowed.contains(techniqueId);
    }

    /**
     * Records that the player borrowed this technique's manual and a {@code player_borrow} event
     * (timed like {@link #joinSect}). Throws IllegalArgumentException with the reason
     * {@code not_member}, {@code not_borrowable} or {@code already_borrowed}, checked in that order.
     */
    public SimEvent recordBorrow(String playerId, String playerName, String techniqueId) {
        return PlayerAffairs.borrow(ctx, playerId, playerName, techniqueId);
    }

    /** Refreshes a player's name and qualification snapshot; does nothing when the player has no record. */
    public void updatePlayerQualification(String playerId, String playerName, PlayerQualification q) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null) {
            return;
        }
        m.playerName = playerName;
        m.realmId = q.realmId();
        m.stageIndex = q.stageIndex();
        m.awakened = q.awakened();
        m.rootPeakBp = q.rootPeakBp();
    }

    // ------------------------------------------------------------------ tasks and masters (sect entry, slice 3)

    /**
     * The player's open sect task joined with its row of {@code sect_tasks.json}, or empty when the
     * player has no record, no open task, or a task id the data no longer has. {@code ready} is
     * {@code progress >= count}, except tribute, which the ledger never calls ready (the runtime
     * checks the inventory at turn-in).
     */
    public Optional<TaskView> task(String playerId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null || m.taskId.isEmpty()) {
            return Optional.empty();
        }
        ContentTables.SectTask t = ctx.data.sectTask(m.taskId);
        if (t == null) {
            return Optional.empty();
        }
        boolean ready = !ContentTables.TASK_TRIBUTE.equals(t.kind()) && m.taskProgress >= t.count();
        String target = m.taskTargetSectId < 0 ? "" : ctx.sectName(m.taskTargetSectId);
        return Optional.of(new TaskView(t.id(), t.kind(), t.count(), t.contribution(), m.taskProgress,
                m.taskTargetSectId, target, m.taskYear, ready));
    }

    /**
     * The task the steward would hand this player now; computed, never recorded. One task a sim
     * year: empty for a player in no sect, with an open task, who already took one this year, or
     * when there is nothing to give. The pick is {@code SimRng.at(seed, day, hash(playerId),
     * Purpose.PLAYER_TASK)} over {@code sect_tasks.json}; a courier's destination is another active
     * sect from the same rng (no other active sect: no courier). {@link #acceptTask} records it.
     */
    public Optional<TaskView> offerTask(String playerId) {
        throw new UnsupportedOperationException("slice 3 package S3-A");
    }

    /**
     * Records the task {@link #offerTask} gives (progress 0, {@code taskYear} this year) and its
     * event (timed like {@link #joinSect}). Throws IllegalArgumentException with the reason
     * {@code not_member}, {@code task_active}, {@code task_done_this_year} or {@code no_task}.
     */
    public SimEvent acceptTask(String playerId, String playerName) {
        throw new UnsupportedOperationException("slice 3 package S3-A");
    }

    /**
     * Adds {@code amount} to the open task's progress, capped at its count. False (nothing changes)
     * when the player has no open task or its kind is not {@code kind}.
     */
    public boolean advanceTask(String playerId, String kind, int amount) {
        throw new UnsupportedOperationException("slice 3 package S3-A");
    }

    /**
     * Turns in the open task: contribution += its contribution, a {@code player_task_done} event
     * (timed like {@link #joinSect}), the task cleared but {@code taskYear} kept. Tribute is judged
     * by the caller, who has already checked and taken the stones. Throws IllegalArgumentException
     * with the reason {@code no_task} or {@code not_ready}.
     */
    public SimEvent completeTask(String playerId, String playerName) {
        throw new UnsupportedOperationException("slice 3 package S3-A");
    }

    /**
     * Takes {@code masterId} (an elder or the sect master at the sect) as the player's master and
     * records {@code player_apprentice} (timed like {@link #joinSect}). Throws
     * IllegalArgumentException with the reason {@code not_member}, {@code rank_too_low},
     * {@code has_master} or {@code master_not_here}.
     */
    public SimEvent apprentice(String playerId, String playerName, int masterId) {
        throw new UnsupportedOperationException("slice 3 package S3-A");
    }

    private PlayerMemberView playerMemberView(PlayerMember m) {
        return new PlayerMemberView(m.playerId, m.playerName, m.sectId, m.sectId < 0 ? "" : ctx.sectName(m.sectId),
                m.rank, m.joinedDay, m.masterId, m.masterId < 0 ? "" : ctx.nameOf(m.masterId), m.contribution,
                m.borrowed, m.standings, m.leftSectId, m.leftDay, m.realmId, m.stageIndex, m.awakened,
                m.rootPeakBp, m.taskId, m.taskProgress, m.taskTargetSectId, m.taskYear);
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
