package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.engine.TextKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The admin acts of the facade: {@code declareWar} and {@code destroySect} (sect entry slice 4). */
class WorldSimAdminActsTest {
    private static final int DPY = 6;
    private static final long SEED = 21;
    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final PlayerQualification PLAIN = new PlayerQualification("mortal", 1, true, 2500);

    private static int[] twoActiveSects(WorldSim sim) {
        List<SectView> active = sim.sects(false);
        assertTrue(active.size() >= 2, "the fixture world has at least two active sects");
        return new int[] {active.get(0).id(), active.get(1).id()};
    }

    /** Two active sects not at war with each other. */
    private static int[] twoAtPeace(WorldSim sim) {
        List<SectView> active = sim.sects(false);
        for (SectView a : active) {
            for (SectView b : active) {
                if (a.id() != b.id() && !"war".equals(stateTowards(sim, a.id(), b.id()))) {
                    return new int[] {a.id(), b.id()};
                }
            }
        }
        throw new AssertionError("the fixture world has two active sects at peace");
    }

    private static String stateTowards(WorldSim sim, int sectId, int other) {
        return sim.sect(sectId).orElseThrow().relations().stream().filter(r -> r.otherSectId() == other)
                .map(SectView.Relation::state).findFirst().orElse("none");
    }

    private static String reason(Runnable act) {
        return assertThrows(IllegalArgumentException.class, act::run).getMessage();
    }

    @Test
    void declareWarPutsBothSectsAtWarWithTheWarLine() {
        WorldSim sim = SimFixtures.genesis(SEED, "small", DPY);
        int[] s = twoAtPeace(sim);
        SimEvent e = sim.declareWar(s[0], s[1]);
        assertEquals("war", e.type());
        assertEquals(3, e.importance());
        assertEquals(List.of(s[0], s[1]), e.sects());
        assertTrue(e.textKey().startsWith(TextKeys.WAR_DECLARE + "."), e.textKey());
        assertEquals("war", stateTowards(sim, s[0], s[1]));
        assertEquals("war", stateTowards(sim, s[1], s[0]));
        assertTrue(sim.event(e.id()).isPresent(), "the event is in the chronicle at once");
        // the next settled day does not return it again
        List<SimEvent> next = sim.step(DPY);
        assertTrue(next.stream().noneMatch(x -> x.id() == e.id()));
        assertEquals("already_at_war", reason(() -> sim.declareWar(s[1], s[0])));
    }

    @Test
    void declareWarRefusesNonsense() {
        WorldSim sim = SimFixtures.genesis(SEED, "small", DPY);
        int[] s = twoActiveSects(sim);
        assertEquals("same_sect", reason(() -> sim.declareWar(s[0], s[0])));
        assertEquals("no_sect", reason(() -> sim.declareWar(s[0], 99_999)));
        sim.destroySect(s[1]);
        assertEquals("sect_inactive", reason(() -> sim.declareWar(s[0], s[1])));
        assertEquals("sect_inactive", reason(() -> sim.destroySect(s[1])));
        assertEquals("no_sect", reason(() -> sim.destroySect(99_999)));
    }

    @Test
    void destroySectEndsItAndItsPlayersBecomeRogues() {
        WorldSim sim = SimFixtures.genesis(SEED, "small", DPY);
        int[] s = twoActiveSects(sim);
        sim.joinSect(ALICE, "Alice", s[0], PLAIN, true);
        SimEvent e = sim.destroySect(s[0]);
        assertEquals("sect_destroyed", e.type());
        assertEquals(3, e.importance());
        assertEquals(List.of(s[0]), e.sects());
        assertTrue(e.textKey().startsWith(TextKeys.SECT_RUIN + "."), e.textKey());
        SectView sect = sim.sect(s[0]).orElseThrow();
        assertEquals("destroyed", sect.state());
        assertEquals(0, sect.memberCount());
        assertTrue(sim.membersAt(s[0]).isEmpty());
        PlayerMemberView alice = sim.playerMember(ALICE).orElseThrow();
        assertFalse(alice.inSect());
        assertEquals(s[0], alice.leftSectId());
        List<SimEvent> after = sim.recentEvents(2, 50, x -> x.id() >= e.id());
        List<SimEvent> gone = after.stream()
                .filter(x -> x.textKey().startsWith(TextKeys.PLAYER_LEAVE_SECT_GONE + ".")).toList();
        assertEquals(1, gone.size(), after.toString());
        assertEquals("player_leave", gone.get(0).type());
        assertEquals(e.id(), gone.get(0).causeId());
        assertEquals(List.of("Alice", sect.name()), gone.get(0).params());
        List<SimEvent> next = sim.step(DPY);
        assertTrue(next.stream().noneMatch(x -> x.id() <= gone.get(0).id()), "closed: not returned again");
    }
}
