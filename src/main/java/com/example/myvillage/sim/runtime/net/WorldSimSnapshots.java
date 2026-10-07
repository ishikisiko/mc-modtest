package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.sim.Overview;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.RegionView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.TaskView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.data.ContentTables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds the {@link WorldSimSnapshot} that answers one {@link WorldSimQuery} from a live ledger.
 * Pure: no Minecraft types, so it is unit-tested against a genesis world. Which sections each kind
 * fills is the table in {@link WorldSimSnapshot}; every list is capped by its {@code MAX_*} constant
 * and every person, sect and region name is resolved here.
 */
public final class WorldSimSnapshots {
    /** Least importance of a chronicle line or of a sect's recent events (notable and major). */
    static final int NOTABLE = 2;
    /** The eight bearings clockwise from north, the tails of {@code world_sim.bearing.*}. */
    public static final List<String> BEARINGS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");

    private WorldSimSnapshots() {
    }

    /** The answer while the ledger is inactive; a missing reason reads "?". */
    public static WorldSimSnapshot inactive(WorldSimQuery query, String reason) {
        return WorldSimSnapshot.inactive(query, reason == null || reason.isEmpty() ? "?" : reason);
    }

    /**
     * Answers {@code query} from {@code sim}.
     *
     * @param daysPerYear  the calendar's current days per year
     * @param calendarDay  the cultivation calendar's day index
     * @param paused       settlement paused by command
     * @param pendingDays  sim days waiting to be settled
     * @param regionName   region id to display name (called only with non-empty ids)
     * @param hereRegionId the asking player's region ({@link WorldSimQuery.Kind#HERE}); empty outside every region
     * @param playerX      the asking player's x, for the distance to a gate
     * @param playerZ      the asking player's z
     * @param playerId     the asking player's UUID string, for {@link WorldSimSnapshot#mine()}; "" or null for none
     */
    public static WorldSimSnapshot build(WorldSim sim, int daysPerYear, long calendarDay, boolean paused,
                                         int pendingDays, Function<String, String> regionName,
                                         Optional<String> hereRegionId, double playerX, double playerZ,
                                         String playerId, WorldSimQuery query) {
        Objects.requireNonNull(sim, "sim");
        Objects.requireNonNull(query, "query");
        Builder b = new Builder(sim, daysPerYear, regionName);
        WorldSimSnapshot.MySect mine = null;
        WorldSimSnapshot.Overview overview = null;
        List<WorldSimSnapshot.SectSummary> sects = List.of();
        WorldSimSnapshot.SectDetail sect = null;
        List<WorldSimSnapshot.PersonSummary> persons = List.of();
        WorldSimSnapshot.PersonDetail person = null;
        List<SimEvent> events = List.of();
        WorldSimSnapshot.Region region = null;

        switch (query.kind()) {
            case OVERVIEW -> {
                Overview o = sim.overview(daysPerYear);
                overview = b.overview(o, paused, pendingDays, calendarDay);
                List<WorldSimSnapshot.PersonSummary> top = new ArrayList<>();
                for (int id : o.topPersonIds()) {
                    if (top.size() >= WorldSimSnapshot.MAX_PRESENT) {
                        break;
                    }
                    sim.person(id).ifPresent(p -> top.add(b.personSummary(p)));
                }
                persons = top;
                mine = b.mySect(playerId);
            }
            case SECTS -> {
                List<SectView> all = new ArrayList<>(sim.sects(true));
                all.sort(Comparator.comparing((SectView s) -> !isActive(s)).thenComparingInt(SectView::id));
                List<WorldSimSnapshot.SectSummary> out = new ArrayList<>();
                for (int i = 0; i < Math.min(WorldSimSnapshot.MAX_SECTS, all.size()); i++) {
                    out.add(b.sectSummary(all.get(i), -1, ""));
                }
                sects = out;
            }
            case SECT -> {
                Optional<SectView> found = query.id() < 0 ? Optional.empty() : sim.sect(query.id());
                if (found.isPresent()) {
                    SectView s = found.get();
                    sect = b.sectDetail(s);
                    persons = b.personSummaries(strongestFirst(sim.membersAt(s.id()), sim.realmIds()),
                            WorldSimSnapshot.MAX_MEMBERS);
                    int sectId = s.id();
                    events = sim.recentEvents(NOTABLE, WorldSimSnapshot.MAX_RELATED,
                            e -> e.sects().contains(sectId) || (e.subject() >= 0 && sim.sectOf(e.subject()) == sectId));
                    WorldSimSnapshot.MySect own = b.mySect(playerId);
                    mine = own != null && own.sectId() == sectId ? own : null;
                }
            }
            case PERSON_SEARCH -> {
                if (!query.text().isEmpty()) {
                    persons = b.personSummaries(sim.findPersons(query.text(), WorldSimSnapshot.MAX_SEARCH),
                            WorldSimSnapshot.MAX_SEARCH);
                }
            }
            case PERSON -> {
                Optional<PersonView> found = query.id() < 0 ? Optional.empty() : sim.person(query.id());
                if (found.isPresent()) {
                    PersonView p = found.get();
                    person = b.personDetail(p);
                    int personId = p.id();
                    events = sim.recentEvents(Integer.MIN_VALUE, WorldSimSnapshot.MAX_RELATED,
                            e -> e.actors().contains(personId));
                }
            }
            case CHRONICLE -> events = sim.recentEvents(NOTABLE, WorldSimSnapshot.MAX_CHRONICLE);
            case HERE -> {
                Optional<String> here = hereRegionId == null ? Optional.empty() : hereRegionId;
                if (here.isPresent() && sim.hasRegion(here.get())) {
                    RegionView r = sim.region(here.get(), daysPerYear);
                    region = new WorldSimSnapshot.Region(r.id(), b.regionName(r.id()), r.tier(), r.qiLo(),
                            r.qiHi(), r.dangerLo(), r.dangerHi(), r.admitsSects(), r.livingCount());
                    List<WorldSimSnapshot.SectSummary> seated = new ArrayList<>();
                    for (int id : r.sectIds()) {
                        if (seated.size() >= WorldSimSnapshot.MAX_SECTS) {
                            break;
                        }
                        sim.sect(id).ifPresent(s -> seated.add(b.sectSummary(s, distance(s, playerX, playerZ),
                                bearing(s.gateX() + 0.5 - playerX, s.gateZ() + 0.5 - playerZ))));
                    }
                    sects = seated;
                    persons = b.personSummaries(strongestFirst(sim.livingIn(r.id()), sim.realmIds()),
                            WorldSimSnapshot.MAX_PRESENT);
                    List<SimEvent> recent = r.recentEvents();
                    events = recent.subList(Math.max(0, recent.size() - WorldSimSnapshot.MAX_RELATED), recent.size());
                }
            }
        }

        List<WorldSimSnapshot.EventLine> lines = new ArrayList<>(events.size());
        Set<Long> causeIds = new LinkedHashSet<>();
        for (SimEvent e : events) {
            lines.add(eventLine(e));
            if (e.causeId() >= 0) {
                causeIds.add(e.causeId());
            }
        }
        List<SimEvent> causeEvents = new ArrayList<>();
        for (long id : causeIds) {
            sim.event(id).ifPresent(causeEvents::add);
        }
        causeEvents.sort(Comparator.comparingLong(SimEvent::id));
        List<WorldSimSnapshot.EventLine> causes = new ArrayList<>(causeEvents.size());
        for (SimEvent e : causeEvents) {
            causes.add(eventLine(e));
        }
        return new WorldSimSnapshot(query, true, "", sim.day(), sim.prehistoryDays(), daysPerYear, overview, sects,
                sect, persons, person, lines, causes, region, mine);
    }

    /**
     * People sorted by realm (in {@code realmOrder}, weakest first), then stage, strongest first;
     * ties by id. A realm missing from the order sorts below every known one.
     */
    public static List<PersonView> strongestFirst(List<PersonView> people, List<String> realmOrder) {
        List<PersonView> sorted = new ArrayList<>(people);
        sorted.sort(Comparator.<PersonView>comparingInt(p -> realmOrder.indexOf(p.realmId())).reversed()
                .thenComparing(Comparator.comparingInt(PersonView::stage).reversed())
                .thenComparingInt(PersonView::id));
        return sorted;
    }

    /** One chronicle line; the subject is the first actor or -1. */
    public static WorldSimSnapshot.EventLine eventLine(SimEvent e) {
        return new WorldSimSnapshot.EventLine(e.id(), e.day(), e.importance(), e.subject(), e.textKey(), e.params(),
                e.causeId());
    }

    /**
     * The eight-way bearing of an offset, one of {@link #BEARINGS}: {@code dx} east, {@code dz} south
     * (Minecraft's +x and +z), measured clockwise from north, each bearing covering 45 degrees centred
     * on its direction; on a boundary the clockwise neighbour wins. No offset reads {@code "n"}.
     */
    public static String bearing(double dx, double dz) {
        if (dx == 0 && dz == 0) {
            return BEARINGS.get(0);
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0) {
            degrees += 360;
        }
        int sector = (int) Math.floor(degrees / 45.0 + 0.5) % BEARINGS.size();
        return BEARINGS.get(sector);
    }

    /** Blocks from (x, z) to the centre of the sect's gate column, rounded. */
    static int distance(SectView s, double x, double z) {
        double dx = s.gateX() + 0.5 - x;
        double dz = s.gateZ() + 0.5 - z;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(dx * dx + dz * dz)));
    }

    private static boolean isActive(SectView s) {
        return "active".equals(s.state());
    }

    /** Resolves names against one ledger. */
    private static final class Builder {
        private final WorldSim sim;
        /** For the ages the portraits are drawn at. */
        private final int daysPerYear;
        private final Function<String, String> regionNames;

        Builder(WorldSim sim, int daysPerYear, Function<String, String> regionNames) {
            this.sim = sim;
            this.daysPerYear = daysPerYear;
            this.regionNames = regionNames == null ? id -> id : regionNames;
        }

        String regionName(String regionId) {
            if (regionId == null || regionId.isEmpty()) {
                return "";
            }
            String name = regionNames.apply(regionId);
            return name == null ? regionId : name;
        }

        String personName(int id) {
            return id < 0 ? "" : sim.nameOf(id);
        }

        String sectName(int id) {
            return id < 0 ? "" : sim.sect(id).map(SectView::name).orElse("");
        }

        WorldSimSnapshot.Overview overview(Overview o, boolean paused, int pendingDays, long calendarDay) {
            List<WorldSimSnapshot.RealmCount> realms = new ArrayList<>();
            for (Map.Entry<String, Integer> e : o.livingByRealm().entrySet()) {
                realms.add(new WorldSimSnapshot.RealmCount(e.getKey(), e.getValue()));
            }
            return new WorldSimSnapshot.Overview(o.tierId(), o.population(), o.targetPopulation(), realms,
                    o.activeSects(), o.destroyedSects(), o.deadCount(), o.eventCount(), paused, pendingDays,
                    calendarDay);
        }

        WorldSimSnapshot.SectSummary sectSummary(SectView s, int distance, String bearing) {
            return new WorldSimSnapshot.SectSummary(s.id(), s.name(), regionName(s.regionId()), s.masterName(),
                    s.memberCount(), s.topRealmId(), (int) Math.round(s.prestige()), s.gateX(), s.gateZ(),
                    s.gateRealized(), isActive(s), distance, bearing);
        }

        /** The player's record in their sect, or null without a record, a sect, or a player. */
        WorldSimSnapshot.MySect mySect(String playerId) {
            if (playerId == null || playerId.isEmpty()) {
                return null;
            }
            Optional<PlayerMemberView> found = sim.playerMember(playerId);
            if (found.isEmpty() || !found.get().inSect()) {
                return null;
            }
            PlayerMemberView m = found.get();
            Optional<SectView> sect = sim.sect(m.sectId());
            String name = m.sectName().isEmpty() ? sect.map(SectView::name).orElse("") : m.sectName();
            // The task line carries the task id (the client names it); a tribute keeps no progress.
            Optional<TaskView> task = sim.task(playerId);
            String taskId = task.map(TaskView::id).orElse("");
            int taskProgress = task.map(t -> ContentTables.TASK_TRIBUTE.equals(t.kind()) ? 0 : t.progress())
                    .orElse(0);
            int taskCount = task.map(TaskView::count).orElse(0);
            return new WorldSimSnapshot.MySect(m.sectId(), name, m.rank(), m.joinedDay(), m.masterName(),
                    m.contribution(), m.standings().getOrDefault(m.sectId(), 0), m.borrowed().size(),
                    sect.map(WorldSimSnapshots::isActive).orElse(false), taskId, taskProgress, taskCount);
        }

        WorldSimSnapshot.SectDetail sectDetail(SectView s) {
            List<WorldSimSnapshot.SectRelation> relations = new ArrayList<>();
            for (SectView.Relation r : s.relations()) {
                relations.add(new WorldSimSnapshot.SectRelation(r.otherSectId(), sectName(r.otherSectId()),
                        r.value(), r.state()));
            }
            return new WorldSimSnapshot.SectDetail(sectSummary(s, -1, ""), s.foundedDay(), s.founderId(),
                    s.founderName(), s.masterId(), s.parentSectId(), sectName(s.parentSectId()),
                    isActive(s) ? -1 : s.destroyedDay(), (int) Math.round(s.resources()),
                    s.signatureTechniqueName(), s.heritageName().isEmpty() ? null : s.heritageName(), relations);
        }

        WorldSimSnapshot.PersonSummary personSummary(PersonView p) {
            return new WorldSimSnapshot.PersonSummary(p.id(), p.name(), p.title(), p.alive(), p.realmId(), p.stage(),
                    p.sectId(), p.sectId() >= 0 ? p.sectName() : "", p.rank(),
                    PortraitAssign.of(p, sim.day(), daysPerYear));
        }

        List<WorldSimSnapshot.PersonSummary> personSummaries(List<PersonView> people, int cap) {
            List<WorldSimSnapshot.PersonSummary> out = new ArrayList<>();
            for (int i = 0; i < Math.min(cap, people.size()); i++) {
                out.add(personSummary(people.get(i)));
            }
            return out;
        }

        WorldSimSnapshot.PersonDetail personDetail(PersonView p) {
            List<WorldSimSnapshot.PersonRelation> relations = new ArrayList<>();
            for (PersonView.Relation r : p.relations()) {
                relations.add(new WorldSimSnapshot.PersonRelation(r.otherId(), personName(r.otherId()), r.kind(),
                        r.strength()));
            }
            return new WorldSimSnapshot.PersonDetail(personSummary(p), p.gender(), p.birthDay(),
                    p.alive() ? -1 : p.deathDay(), p.alive() ? "" : p.deathCause(), p.alive() ? -1 : p.killerId(),
                    p.alive() ? "" : personName(p.killerId()), p.rootGrade(), p.alive() ? p.root() : List.of(),
                    p.alive() ? p.progress() : 0.0, p.masterId(), personName(p.masterId()),
                    regionName(p.regionId()), p.status(), p.techniqueName(), p.alive() ? p.injury() : 0, relations);
        }
    }
}
