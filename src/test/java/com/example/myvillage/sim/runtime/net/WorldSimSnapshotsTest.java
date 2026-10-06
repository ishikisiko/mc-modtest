package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.Overview;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.RegionView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The snapshot builder against a genesis world: sections per kind, caps, names and causes. */
class WorldSimSnapshotsTest {
    private static final int DPY = NetFixtures.DAYS_PER_YEAR;
    private static WorldSim sim;

    @BeforeAll
    static void world() {
        sim = NetFixtures.world();
    }

    private static WorldSimSnapshot build(WorldSimQuery q) {
        return build(q, Optional.empty(), 0, 0);
    }

    private static WorldSimSnapshot build(WorldSimQuery q, Optional<String> here, double x, double z) {
        WorldSimSnapshot s = WorldSimSnapshots.build(sim, DPY, 1234L, true, 7, NetFixtures.REGION_NAME, here, x, z, q);
        assertEquals(q, s.query());
        assertTrue(s.active());
        assertEquals("", s.inactiveReason());
        assertEquals(sim.day(), s.day());
        assertEquals(sim.prehistoryDays(), s.prehistoryDays());
        assertEquals(DPY, s.daysPerYear());
        assertCausesAreReferencedAndKept(s);
        return s;
    }

    /** {@code causes} holds exactly the kept events that {@code events} point at, oldest first. */
    private static void assertCausesAreReferencedAndKept(WorldSimSnapshot s) {
        Set<Long> expected = new HashSet<>();
        for (WorldSimSnapshot.EventLine e : s.events()) {
            if (e.causeId() >= 0 && sim.event(e.causeId()).isPresent()) {
                expected.add(e.causeId());
            }
        }
        Set<Long> actual = new HashSet<>();
        long last = Long.MIN_VALUE;
        for (WorldSimSnapshot.EventLine c : s.causes()) {
            assertTrue(c.id() > last, "causes are oldest first without repeats");
            last = c.id();
            actual.add(c.id());
            assertEquals(WorldSimSnapshots.eventLine(sim.event(c.id()).orElseThrow()), c);
        }
        assertEquals(expected, actual);
        for (WorldSimSnapshot.EventLine e : s.events()) {
            WorldSimSnapshot.EventLine cause = s.causeOf(e);
            if (cause != null) {
                assertEquals(e.causeId(), cause.id());
            }
        }
    }

    private static void assertEventLines(List<SimEvent> expected, List<WorldSimSnapshot.EventLine> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            SimEvent e = expected.get(i);
            WorldSimSnapshot.EventLine l = actual.get(i);
            assertEquals(e.id(), l.id());
            assertEquals(e.day(), l.day());
            assertEquals(e.importance(), l.importance());
            assertEquals(e.actors().isEmpty() ? -1 : e.actors().get(0), l.subjectId(), "subject is the first actor");
            assertEquals(e.textKey(), l.textKey());
            assertEquals(e.params(), l.params());
            assertEquals(e.causeId(), l.causeId());
        }
    }

    private static void assertOnly(WorldSimSnapshot s, String... filled) {
        Set<String> f = Set.of(filled);
        assertEquals(f.contains("overview"), s.overview() != null, "overview");
        assertEquals(f.contains("sects"), !s.sects().isEmpty(), "sects");
        assertEquals(f.contains("sect"), s.sect() != null, "sect");
        assertEquals(f.contains("persons"), !s.persons().isEmpty(), "persons");
        assertEquals(f.contains("person"), s.person() != null, "person");
        assertEquals(f.contains("events"), !s.events().isEmpty(), "events");
        assertEquals(f.contains("region"), s.region() != null, "region");
        if (!f.contains("events")) {
            assertTrue(s.causes().isEmpty(), "no causes without events");
        }
    }

    private static List<Integer> ids(List<WorldSimSnapshot.PersonSummary> people) {
        return people.stream().map(WorldSimSnapshot.PersonSummary::id).toList();
    }

    private static List<Integer> viewIds(List<PersonView> people) {
        return people.stream().map(PersonView::id).toList();
    }

    private static List<PersonView> everyone() {
        return sim.findPersons("", Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ kinds

    @Test
    void overviewCarriesTheLedgerSummaryAndTheFiveForemost() {
        WorldSimSnapshot s = build(WorldSimQuery.overview());
        assertOnly(s, "overview", "persons");
        Overview o = sim.overview(DPY);
        WorldSimSnapshot.Overview v = s.overview();
        assertEquals(o.tierId(), v.tierId());
        assertEquals(o.population(), v.population());
        assertEquals(o.targetPopulation(), v.targetPopulation());
        assertEquals(o.activeSects(), v.activeSects());
        assertEquals(o.destroyedSects(), v.destroyedSects());
        assertEquals(o.deadCount(), v.deadCount());
        assertEquals(o.eventCount(), v.eventCount());
        assertTrue(v.paused());
        assertEquals(7, v.pendingDays());
        assertEquals(1234L, v.calendarDay());
        assertEquals(sim.realmIds(), v.livingByRealm().stream().map(WorldSimSnapshot.RealmCount::realmId).toList(),
                "realms in realm order");
        for (WorldSimSnapshot.RealmCount r : v.livingByRealm()) {
            assertEquals(o.livingByRealm().get(r.realmId()), r.count());
        }
        assertEquals(o.topPersonIds(), ids(s.persons()));
        assertEquals(5, s.persons().size());
        for (WorldSimSnapshot.PersonSummary p : s.persons()) {
            PersonView view = sim.person(p.id()).orElseThrow();
            assertEquals(view.name(), p.name());
            assertEquals(view.title(), p.title());
            assertEquals(view.sectName(), p.sectName());
            assertTrue(p.alive());
        }
    }

    @Test
    void sectsListsEverySectActiveFirstThenById() {
        WorldSimSnapshot s = build(WorldSimQuery.sects());
        assertOnly(s, "sects");
        List<SectView> all = sim.sects(true);
        assertEquals(Math.min(WorldSimSnapshot.MAX_SECTS, all.size()), s.sects().size());
        Map<Integer, SectView> byId = new HashMap<>();
        all.forEach(v -> byId.put(v.id(), v));
        boolean seenDestroyed = false;
        int lastId = Integer.MIN_VALUE;
        for (WorldSimSnapshot.SectSummary summary : s.sects()) {
            SectView v = byId.get(summary.id());
            assertNotNull(v);
            assertEquals(v.state().equals("active"), summary.active());
            if (!summary.active() && !seenDestroyed) {
                seenDestroyed = true;
                lastId = Integer.MIN_VALUE;
            }
            assertFalse(seenDestroyed && summary.active(), "active sects come first");
            assertTrue(summary.id() > lastId, "then id order");
            lastId = summary.id();
            assertEquals(v.name(), summary.name());
            assertEquals("域:" + v.regionId(), summary.regionName(), "region names go through the resolver");
            assertEquals(v.masterName(), summary.masterName());
            assertEquals(Math.round(v.prestige()), summary.prestige());
            assertEquals(-1, summary.distance());
        }
    }

    @Test
    void sectDetailCarriesTheHeritageNameOrNull() {
        for (SectView v : sim.sects(true)) {
            WorldSimSnapshot.SectDetail d = build(WorldSimQuery.sect(v.id())).sect();
            assertEquals(v.heritageName().isEmpty() ? null : v.heritageName(), d.heritageName(), v.name());
        }
    }

    @Test
    void sectDetailResolvesNamesAndListsMembersStrongestFirst() {
        SectView busiest = sim.sects(false).stream()
                .max(Comparator.comparingInt((SectView v) -> sim.membersAt(v.id()).size())
                        .thenComparingInt(v -> -v.id()))
                .orElseThrow();
        WorldSimSnapshot s = build(WorldSimQuery.sect(busiest.id()));
        assertNotNull(s.sect());
        assertNull(s.overview());
        assertNull(s.person());
        assertNull(s.region());
        assertTrue(s.sects().isEmpty());
        WorldSimSnapshot.SectDetail d = s.sect();
        assertEquals(busiest.id(), d.summary().id());
        assertEquals(busiest.founderName(), d.founderName());
        assertEquals(sim.nameOf(busiest.masterId()), d.summary().masterName());
        assertEquals(busiest.masterId(), d.masterId());
        assertEquals(-1, d.destroyedDay());
        assertEquals(Math.round(busiest.resources()), d.resources());
        assertEquals(busiest.signatureTechniqueName(), d.signatureTechniqueName());
        assertEquals(busiest.relations().size(), d.relations().size());
        for (WorldSimSnapshot.SectRelation r : d.relations()) {
            assertEquals(sim.sect(r.otherSectId()).orElseThrow().name(), r.otherSectName());
        }

        List<PersonView> at = WorldSimSnapshots.strongestFirst(sim.membersAt(busiest.id()), sim.realmIds());
        assertEquals(viewIds(at.subList(0, Math.min(WorldSimSnapshot.MAX_MEMBERS, at.size()))), ids(s.persons()));

        List<SimEvent> related = sim.recentEvents(2, Integer.MAX_VALUE,
                e -> e.sects().contains(busiest.id()) || (e.subject() >= 0 && sim.sectOf(e.subject()) == busiest.id()));
        assertFalse(related.isEmpty(), "a sect after prehistory has a history");
        assertEventLines(related.subList(Math.max(0, related.size() - WorldSimSnapshot.MAX_RELATED), related.size()),
                s.events());
        for (WorldSimSnapshot.EventLine e : s.events()) {
            assertTrue(e.importance() >= 2);
        }
    }

    @Test
    void aSectWithAParentNamesIt() {
        Optional<SectView> child = sim.sects(true).stream().filter(v -> v.parentSectId() >= 0).findFirst();
        for (SectView v : sim.sects(true)) {
            WorldSimSnapshot.SectDetail d = build(WorldSimQuery.sect(v.id())).sect();
            if (v.parentSectId() >= 0) {
                assertEquals(sim.sect(v.parentSectId()).orElseThrow().name(), d.parentSectName());
            } else {
                assertEquals("", d.parentSectName());
            }
            if (!v.state().equals("active")) {
                assertEquals(v.destroyedDay(), d.destroyedDay());
                assertFalse(d.summary().active());
            }
        }
        child.ifPresent(v -> assertFalse(build(WorldSimQuery.sect(v.id())).sect().parentSectName().isEmpty()));
    }

    @Test
    void anUnknownSectOrPersonAnswersEmpty() {
        assertOnly(build(WorldSimQuery.sect(99_999)));
        assertOnly(build(WorldSimQuery.sect(-1)));
        assertOnly(build(WorldSimQuery.person(99_999)));
        assertOnly(build(WorldSimQuery.person(-5)));
    }

    @Test
    void personSearchIsCappedLivingFirstAndEmptyForNoText() {
        assertOnly(build(WorldSimQuery.personSearch("")));
        assertOnly(build(WorldSimQuery.personSearch("   ")));

        // the most common character in names: enough matches to hit the cap. A medium world, since the
        // small fixture's few names need not share any character often enough.
        WorldSim big = WorldSim.genesis(NetFixtures.SEED, NetFixtures.graph(), NetFixtures.data(), "medium", DPY);
        Map<Integer, Integer> counts = new HashMap<>();
        for (PersonView p : big.findPersons("", Integer.MAX_VALUE)) {
            (p.name() + p.title()).codePoints().distinct().forEach(c -> counts.merge(c, 1, Integer::sum));
        }
        int common = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        String text = new String(Character.toChars(common));
        List<PersonView> all = big.findPersons(text, Integer.MAX_VALUE);
        assertTrue(all.size() > WorldSimSnapshot.MAX_SEARCH,
                "the search cap is exercised: '" + text + "' matches " + all.size());

        WorldSimQuery q = WorldSimQuery.personSearch(text);
        WorldSimSnapshot s = WorldSimSnapshots.build(big, DPY, 1234L, true, 7, NetFixtures.REGION_NAME,
                Optional.empty(), 0, 0, q);
        assertEquals(q, s.query());
        assertOnly(s, "persons");
        assertEquals(WorldSimSnapshot.MAX_SEARCH, s.persons().size());
        assertEquals(viewIds(all.subList(0, WorldSimSnapshot.MAX_SEARCH)), ids(s.persons()));
        boolean seenDead = false;
        for (WorldSimSnapshot.PersonSummary p : s.persons()) {
            seenDead |= !p.alive();
            assertFalse(seenDead && p.alive(), "living first");
            assertTrue(p.name().contains(text) || p.title().contains(text));
        }
    }

    @Test
    void aLivingPersonResolvesMasterRegionAndRelations() {
        PersonView p = everyone().stream()
                .filter(v -> v.alive() && v.masterId() >= 0 && !v.relations().isEmpty())
                .findFirst().orElseThrow();
        WorldSimSnapshot s = build(WorldSimQuery.person(p.id()));
        assertNotNull(s.person());
        assertTrue(s.persons().isEmpty());
        assertNull(s.sect());
        WorldSimSnapshot.PersonDetail d = s.person();
        assertEquals(p.id(), d.summary().id());
        assertTrue(d.summary().alive());
        assertEquals(sim.nameOf(p.masterId()), d.masterName());
        assertFalse(d.masterName().isEmpty());
        assertEquals("域:" + p.regionId(), d.regionName());
        assertEquals(p.root(), d.root());
        assertEquals(10_000, d.root().stream().mapToInt(Integer::intValue).sum());
        assertEquals(p.progress(), d.progress());
        assertEquals(-1, d.deathDay());
        assertEquals(-1, d.killerId());
        assertEquals("", d.killerName());
        assertEquals(p.relations().size(), d.relations().size());
        for (WorldSimSnapshot.PersonRelation r : d.relations()) {
            assertEquals(sim.nameOf(r.otherId()), r.otherName());
            assertFalse(r.otherName().isEmpty());
        }
        if (p.sectId() >= 0) {
            assertEquals(p.sectName(), d.summary().sectName());
        }
        List<SimEvent> mine = sim.recentEvents(Integer.MIN_VALUE, Integer.MAX_VALUE, e -> e.actors().contains(p.id()));
        assertEventLines(mine.subList(Math.max(0, mine.size() - WorldSimSnapshot.MAX_RELATED), mine.size()),
                s.events());
    }

    @Test
    void theDeadKeepTheirKillerAndLoseLivingFields() {
        PersonView dead = everyone().stream().filter(v -> !v.alive() && v.killerId() >= 0).findFirst().orElseThrow();
        WorldSimSnapshot.PersonDetail d = build(WorldSimQuery.person(dead.id())).person();
        assertFalse(d.summary().alive());
        assertEquals(dead.killerId(), d.killerId());
        assertEquals(sim.nameOf(dead.killerId()), d.killerName());
        assertFalse(d.killerName().isEmpty());
        assertEquals(dead.deathDay(), d.deathDay());
        assertEquals(dead.deathCause(), d.deathCause());
        assertEquals("dead", d.status());
        assertTrue(d.root().isEmpty());
        assertEquals(0.0, d.progress());
        assertEquals("", d.regionName());
    }

    @Test
    void theChronicleIsTheLatestNotableEventsWithTheirKeptCauses() {
        WorldSimSnapshot s = build(WorldSimQuery.chronicle());
        assertOnly(s, "events");
        List<SimEvent> expected = sim.recentEvents(2, WorldSimSnapshot.MAX_CHRONICLE);
        assertEquals(WorldSimSnapshot.MAX_CHRONICLE, expected.size(), "the chronicle cap is exercised");
        assertEventLines(expected, s.events());
    }

    @Test
    void causesSkipPrunedEventsAndAreNotDuplicated() {
        // Over every person's recent events: at least one cause is still kept, and pruned ones are left out.
        int kept = 0;
        for (PersonView p : everyone()) {
            WorldSimSnapshot s = WorldSimSnapshots.build(sim, DPY, 0, false, 0, NetFixtures.REGION_NAME,
                    Optional.empty(), 0, 0, WorldSimQuery.person(p.id()));
            assertCausesAreReferencedAndKept(s);
            kept += s.causes().size();
        }
        assertTrue(kept > 0, "some event in the world has a kept cause");
        WorldSimSnapshot chronicle = build(WorldSimQuery.chronicle());
        Set<Long> referenced = new HashSet<>();
        chronicle.events().forEach(e -> referenced.add(e.causeId()));
        for (WorldSimSnapshot.EventLine c : chronicle.causes()) {
            assertTrue(referenced.contains(c.id()), "a cause is only sent when an event points at it");
        }
    }

    @Test
    void hereListsSeatedSectsWithDistanceStrongestPresentAndRecentEvents() {
        SectView seat = sim.sects(false).get(0);
        String regionId = seat.regionId();
        double px = seat.gateX() + 0.5 + 30;
        double pz = seat.gateZ() + 0.5 - 40;
        WorldSimSnapshot s = build(WorldSimQuery.here(), Optional.of(regionId), px, pz);
        RegionView r = sim.region(regionId, DPY);
        WorldSimSnapshot.Region region = s.region();
        assertNotNull(region);
        assertEquals(regionId, region.id());
        assertEquals("域:" + regionId, region.displayName());
        assertEquals(r.tier(), region.tier());
        assertEquals(r.qiLo(), region.qiLo());
        assertEquals(r.dangerHi(), region.dangerHi());
        assertEquals(r.admitsSects(), region.admitsSects());
        assertEquals(r.livingCount(), region.livingCount());

        assertEquals(r.sectIds(), s.sects().stream().map(WorldSimSnapshot.SectSummary::id).toList());
        WorldSimSnapshot.SectSummary mine = s.sects().stream().filter(x -> x.id() == seat.id()).findFirst().orElseThrow();
        assertEquals(50, mine.distance(), "a 30/40 offset is 50 blocks");

        List<PersonView> present = WorldSimSnapshots.strongestFirst(sim.livingIn(regionId), sim.realmIds());
        assertEquals(r.livingCount(), present.size());
        assertEquals(viewIds(present.subList(0, Math.min(WorldSimSnapshot.MAX_PRESENT, present.size()))),
                ids(s.persons()));
        List<SimEvent> recent = r.recentEvents();
        assertEventLines(recent.subList(Math.max(0, recent.size() - WorldSimSnapshot.MAX_RELATED), recent.size()),
                s.events());
        assertNull(s.overview());
        assertNull(s.sect());
        assertNull(s.person());
    }

    @Test
    void theCapOnPeoplePresentBites() {
        String crowded = null;
        int most = -1;
        for (SectView v : sim.sects(false)) {
            int n = sim.livingIn(v.regionId()).size();
            if (n > most) {
                most = n;
                crowded = v.regionId();
            }
        }
        assertTrue(most > WorldSimSnapshot.MAX_PRESENT, "some region holds more people than the cap");
        WorldSimSnapshot s = build(WorldSimQuery.here(), Optional.of(crowded), 0, 0);
        assertEquals(WorldSimSnapshot.MAX_PRESENT, s.persons().size());
    }

    @Test
    void hereOutsideEveryRegionHasNoRegionAndNothingElse() {
        assertOnly(build(WorldSimQuery.here(), Optional.empty(), 0, 0));
        assertOnly(build(WorldSimQuery.here(), Optional.of("no_such_region"), 0, 0));
    }

    @Test
    void inactiveCarriesOnlyTheReason() {
        WorldSimSnapshot s = WorldSimSnapshots.inactive(WorldSimQuery.chronicle(), "the data failed to load");
        assertFalse(s.active());
        assertEquals("the data failed to load", s.inactiveReason());
        assertEquals(WorldSimQuery.chronicle(), s.query());
        assertNull(s.overview());
        assertTrue(s.events().isEmpty());
        assertEquals("?", WorldSimSnapshots.inactive(WorldSimQuery.here(), null).inactiveReason());
    }

    @Test
    void strongestFirstSortsByRealmThenStageThenId() {
        List<String> order = sim.realmIds();
        List<PersonView> people = new ArrayList<>(everyone().stream().filter(PersonView::alive).toList());
        List<PersonView> sorted = WorldSimSnapshots.strongestFirst(people, order);
        for (int i = 1; i < sorted.size(); i++) {
            PersonView a = sorted.get(i - 1);
            PersonView b = sorted.get(i);
            int ra = order.indexOf(a.realmId());
            int rb = order.indexOf(b.realmId());
            assertTrue(ra > rb || (ra == rb && (a.stage() > b.stage() || (a.stage() == b.stage() && a.id() < b.id()))),
                    a.id() + " before " + b.id());
        }
    }
}
