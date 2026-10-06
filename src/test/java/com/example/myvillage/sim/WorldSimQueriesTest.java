package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The read accessors the 天下 page's snapshot builder uses: event lookup, filtered recency, names. */
class WorldSimQueriesTest {
    private static final int DPY = 6;
    private static WorldSim sim;
    private static List<SimEvent> kept;

    @BeforeAll
    static void world() {
        sim = SimFixtures.genesis(5, "small", DPY);
        kept = sim.recentEvents(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Test
    void everyKeptEventIsFoundByIdAndOthersAreNot() {
        assertFalse(kept.isEmpty());
        Set<Long> ids = new HashSet<>();
        long last = Long.MIN_VALUE;
        for (SimEvent e : kept) {
            assertTrue(e.id() > last, "the chronicle is in id order");
            last = e.id();
            ids.add(e.id());
            assertEquals(e, sim.event(e.id()).orElseThrow());
        }
        assertTrue(sim.event(-1).isEmpty());
        assertTrue(sim.event(last + 1).isEmpty());
        int pruned = 0;
        for (long id = 1; id <= last; id++) {
            if (!ids.contains(id)) {
                assertTrue(sim.event(id).isEmpty(), "pruned event " + id);
                pruned++;
            }
        }
        assertTrue(pruned > 0, "prehistory prunes some minor events");
    }

    @Test
    void filteredRecentEventsAreTheFilteredTailOldestFirst() {
        int subject = kept.get(kept.size() / 2).subject();
        List<SimEvent> expected = new ArrayList<>();
        for (SimEvent e : kept) {
            if (e.importance() >= 1 && e.actors().contains(subject)) {
                expected.add(e);
            }
        }
        List<SimEvent> tail = expected.subList(Math.max(0, expected.size() - 3), expected.size());
        assertEquals(tail, sim.recentEvents(1, 3, e -> e.actors().contains(subject)));
        assertEquals(sim.recentEvents(2, 25), sim.recentEvents(2, 25, e -> true));
        assertTrue(sim.recentEvents(2, 25, e -> false).isEmpty());
        assertTrue(sim.recentEvents(1, 0, e -> true).isEmpty());
    }

    @Test
    void realmIdsAreInRealmOrder() {
        assertEquals(new ArrayList<>(sim.overview(DPY).livingByRealm().keySet()), sim.realmIds());
        assertEquals(SimFixtures.data().realms().realms().size(), sim.realmIds().size());
    }

    @Test
    void namesSectsAndPresenceOfLivingAndDead() {
        List<PersonView> everyone = sim.findPersons("", Integer.MAX_VALUE);
        boolean sawDead = false;
        for (PersonView p : everyone) {
            assertEquals(p.name(), sim.nameOf(p.id()));
            assertEquals(p.sectId(), sim.sectOf(p.id()));
            sawDead |= !p.alive();
        }
        assertTrue(sawDead);
        assertEquals("", sim.nameOf(-1));
        assertEquals(-1, sim.sectOf(-1));
        assertEquals("", sim.nameOf(Integer.MAX_VALUE));

        SectView s = sim.sects(false).get(0);
        assertTrue(sim.hasRegion(s.regionId()));
        assertFalse(sim.hasRegion("no_such_region"));
        List<PersonView> here = sim.livingIn(s.regionId());
        assertEquals(sim.region(s.regionId(), DPY).livingCount(), here.size());
        int last = Integer.MIN_VALUE;
        for (PersonView p : here) {
            assertTrue(p.alive());
            assertEquals(s.regionId(), p.regionId());
            assertTrue(p.id() > last, "id order");
            last = p.id();
        }
    }
}
