package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sim.PersonView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Who is shown, where they stand, and what their name tag says. */
class AvatarPlannerTest {
    private static final List<String> REALMS =
            List.of("qi_refining", "foundation_establishment", "golden_core", "nascent_soul");

    private static PersonView person(int id, String rank, String realm, int stage) {
        return new PersonView(id, "李" + id, "", "male", true, -100, -1, "", -1, List.of(2000, 2000, 2000, 2000, 2000),
                "five", realm, stage, 0.0, 3, "青云宗", rank, -1, "r", "at_sect", "t", "青木长春功", 0, List.of(50, 50, 50, 50, 50),
                List.of());
    }

    @Test
    void theMasterAndEldersComeFirstThenRealmStageAndId() {
        List<PersonView> members = List.of(
                person(1, "outer", "qi_refining", 8),
                person(2, "inner", "foundation_establishment", 0),
                person(3, "elder", "foundation_establishment", 2),
                person(4, "sect_master", "golden_core", 0),
                person(5, "elder", "golden_core", 1),
                person(6, "inner", "foundation_establishment", 0),
                person(7, "outer", "qi_refining", 8));
        List<Integer> ids = AvatarPlanner.select(members, REALMS, 10).stream().map(PersonView::id).toList();
        assertEquals(List.of(4, 5, 3, 2, 6, 1, 7), ids);
        assertEquals(List.of(4, 5, 3), AvatarPlanner.select(members, REALMS, 3).stream().map(PersonView::id).toList());
        assertTrue(AvatarPlanner.select(members, REALMS, 0).isEmpty());
    }

    @Test
    void cellsPreferTheLowestTerraceAndKeepTheirSpacing() {
        List<BlockPos> cells = SectCourtyard.cells(11L, new BlockPos(0, -60, 0), SectCourtyard.variants().getFirst());
        int lowest = cells.get(0).getY();
        List<BlockPos> occupied = new ArrayList<>();
        for (int id = 0; id < 40; id++) {
            BlockPos c = AvatarPlanner.pickCell(id, cells, occupied);
            assertNotNull(c);
            assertTrue(c.getY()>=lowest, "蓝图活动区按标高优先并在满员后向上分配");
            for (BlockPos o : occupied) {
                assertTrue(Math.max(Math.abs(o.getX() - c.getX()), Math.abs(o.getZ() - c.getZ())) >= AvatarPlanner.MIN_SPACING,
                        c + " too close to " + o);
            }
            occupied.add(c);
        }
        assertEquals(occupied.size(), new HashSet<>(occupied).size());
        assertTrue(cells.containsAll(occupied));
    }

    @Test
    void aFullTerraceSpillsUpwardAndAFullCompoundReturnsNull() {
        List<BlockPos> cells = new ArrayList<>();
        IntStream.range(0, 2).forEach(x -> cells.add(new BlockPos(x, 0, 0)));   // adjacent: room for one
        cells.add(new BlockPos(0, 8, 0));
        List<BlockPos> occupied = new ArrayList<>();
        for (int id = 0; id < 2; id++) {
            occupied.add(AvatarPlanner.pickCell(id, cells, occupied));
        }
        assertEquals(List.of(0, 8), occupied.stream().map(BlockPos::getY).toList());
        assertNull(AvatarPlanner.pickCell(99, cells, occupied));
    }

    @Test
    void aPersonStandsAtTheSamePlaceEachTime() {
        List<BlockPos> cells = SectCourtyard.cells(11L, new BlockPos(0, -60, 0), SectCourtyard.variants().getFirst());
        assertEquals(AvatarPlanner.pickCell(17, cells, List.of()), AvatarPlanner.pickCell(17, cells, List.of()));
        assertEquals(AvatarPlanner.yaw(17), AvatarPlanner.yaw(17));
    }

    @Test
    void theNameTagContainsNameAndSectWithoutPublishingRealm() {
        Component name = WorldSimAvatars.name(person(9, "elder", "golden_core", 1));
        TranslatableContents contents = (TranslatableContents) name.getContents();
        assertEquals(WorldSimAvatars.NAME_KEY, contents.getKey());
        Object[] args = contents.getArgs();
        assertEquals(2, args.length);
        assertEquals("李9", args[0]);
        assertEquals("青云宗", args[1]);
        assertEquals(name, WorldSimAvatars.name(person(9, "elder", "nascent_soul", 0)),
                "A realm change must not change the passive name tag");
    }

    @Test
    void theStewardComesFirstOfAllAndTheRestKeepTheirOrder() {
        List<PersonView> members = List.of(
                person(1, "outer", "qi_refining", 8),
                person(2, "inner", "foundation_establishment", 0),
                person(3, "elder", "foundation_establishment", 2),
                person(4, "sect_master", "golden_core", 0),
                person(5, "elder", "golden_core", 1),
                person(6, "inner", "foundation_establishment", 0),
                person(7, "outer", "qi_refining", 8));
        assertEquals(List.of(7, 4, 5, 3, 2, 6, 1),
                AvatarPlanner.select(members, REALMS, 10, 7).stream().map(PersonView::id).toList());
        assertEquals(List.of(7, 4),
                AvatarPlanner.select(members, REALMS, 2, 7).stream().map(PersonView::id).toList(),
                "the steward is shown even under a tight cap");
        assertEquals(AvatarPlanner.select(members, REALMS, 10),
                AvatarPlanner.select(members, REALMS, 10, -1), "no steward: the old order");
        assertEquals(AvatarPlanner.select(members, REALMS, 10),
                AvatarPlanner.select(members, REALMS, 10, 99), "a steward not at the sect changes nothing");
    }

    @Test
    void theStewardUsesTheDeclaredGateAnchor() {
        BlockPos anchor=new BlockPos(100,-45,-40);
        var data=com.example.myvillage.sect.SectBlueprint.current();
        assertEquals(data.world(anchor,data.steward),SectCourtyard.stewardCell(anchor));
        assertTrue(SectCourtyard.cells(11L,anchor,data.id.toString()).contains(SectCourtyard.stewardCell(anchor)));
    }

    @Test
    void aFullGateTerraceSendsTheStewardUpwardAndAFullCompoundReturnsNull() {
        List<BlockPos> cells = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 8, 0));
        assertEquals(new BlockPos(0, 0, 0), AvatarPlanner.stewardCell(1, cells, List.of(), 0));
        assertEquals(new BlockPos(0, 8, 0), AvatarPlanner.stewardCell(1, cells, List.of(new BlockPos(0, 0, 0)), 0));
        assertNull(AvatarPlanner.stewardCell(1, cells, List.of(new BlockPos(0, 0, 0), new BlockPos(0, 8, 0)), 0));
        assertNull(AvatarPlanner.stewardCell(1, List.of(), List.of(), 0));
    }

    @Test
    void theStewardsTagHasItsOwnKeyAndRolesFollowTheLedger() {
        PersonView outer = person(9, "outer", "qi_refining", 1);
        TranslatableContents steward = (TranslatableContents) WorldSimAvatars.name(outer, "steward").getContents();
        assertEquals(WorldSimAvatars.STEWARD_NAME_KEY, steward.getKey());
        assertEquals(2, steward.getArgs().length);
        assertEquals(WorldSimAvatars.NAME_KEY,
                ((TranslatableContents) WorldSimAvatars.name(outer, "none").getContents()).getKey());
        assertEquals("steward", WorldSimAvatars.role(outer, 9));
        assertEquals("none", WorldSimAvatars.role(outer, 3));
        assertEquals("elder", WorldSimAvatars.role(person(3, "elder", "golden_core", 1), 9));
        assertEquals("elder", WorldSimAvatars.role(person(4, "sect_master", "golden_core", 1), -1));
        assertEquals("steward", WorldSimAvatars.role(person(3, "elder", "golden_core", 1), 3),
                "an elder who is the steward (nobody lower at the gate) is the steward");
    }

    @Test
    void theBuildSeedAndVariantArePerSectAndStable() {
        assertEquals(GateBuilder.seed(42L, 3), GateBuilder.seed(42L, 3));
        assertTrue(GateBuilder.seed(42L, 3) != GateBuilder.seed(42L, 4));
        assertTrue(GateBuilder.seed(42L, 3) != GateBuilder.seed(43L, 3));
        HashSet<String> seen = new HashSet<>();
        for (int sect = 0; sect < 40; sect++) {
            String v = GateBuilder.variant(42L, sect);
            assertTrue(SectCourtyard.variants().contains(v), v);
            seen.add(v);
        }
        assertEquals(SectCourtyard.variants().size(), seen.size(), "every variant occurs over forty sects");
    }
}
