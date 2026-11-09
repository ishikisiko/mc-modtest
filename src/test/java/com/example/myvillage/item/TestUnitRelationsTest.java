package com.example.myvillage.item;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestUnitRelationsTest {
    private final UUID owner = UUID.randomUUID(), aId = UUID.randomUUID(), bId = UUID.randomUUID();
    private TestUnitRelations.Unit unit(int team, int enemies, boolean player, boolean monsters, Map<UUID, Boolean> pairs) {
        return new TestUnitRelations.Unit(owner, team, enemies, player, monsters, pairs);
    }
    private Boolean relation(TestUnitRelations.Unit a, TestUnitRelations.Unit b) {
        return TestUnitRelations.hostile(aId, a, false, false, bId, b, false, false);
    }
    @Test void ordinaryBodiesKeepTheProductionPolicy() {
        assertNull(relation(null, null));
        assertFalse(TestUnitRelations.hostile(aId, null, false, false, aId, null, false, false));
    }
    @Test void eitherTeamCanDeclareMutualHostilityAndUnrelatedTeamsStayNeutral() {
        var a = unit(0, 2, false, false, Map.of()); var b = unit(1, 0, false, false, Map.of());
        assertTrue(relation(a, b));
        assertTrue(TestUnitRelations.hostile(bId, b, false, false, aId, a, false, false));
        assertFalse(relation(a, unit(2, 0, false, false, Map.of())));
    }
    @Test void explicitNeutralityOverridesTheGroupAndSpeciesRules() {
        var a = unit(0, 2, true, true, Map.of(bId, false)); var b = unit(1, 0, false, true, Map.of(aId, false));
        assertFalse(TestUnitRelations.hostile(aId, a, false, false, bId, b, true, false));
        assertFalse(TestUnitRelations.hostile(bId, b, true, false, aId, a, false, false));
    }
    @Test void specificRivalsCanFightWithinTheSameTeam() {
        assertTrue(relation(unit(0, 0, false, false, Map.of(bId, true)), unit(0, 0, false, false, Map.of(aId, true))));
    }
    @Test void testsNeverAdoptAnotherPlayersBodies() {
        var other = new TestUnitRelations.Unit(UUID.randomUUID(), 1, 15, true, true, Map.of(aId, true));
        assertFalse(relation(unit(0, 15, true, true, Map.of(bId, true)), other));
    }
    @Test void playerFlagOnlyIncludesTheOwnerAndIsSymmetric() {
        var a = unit(0, 0, true, false, Map.of());
        assertTrue(TestUnitRelations.hostile(aId, a, false, false, owner, null, false, true));
        assertTrue(TestUnitRelations.hostile(owner, null, false, true, aId, a, false, false));
        assertFalse(TestUnitRelations.hostile(aId, a, false, false, bId, null, false, true));
        assertFalse(TestUnitRelations.hostile(aId, unit(0,0,false,false,Map.of()), false,false,owner,null,false,true));
    }
    @Test void speciesHostilityIncludesWildAndSummonedMonsters() {
        var a = unit(0, 0, false, true, Map.of()); var b = unit(0,0,false,false,Map.of());
        assertTrue(TestUnitRelations.hostile(aId,a,false,false,bId,null,true,false));
        assertTrue(TestUnitRelations.hostile(aId,a,false,false,bId,b,true,false));
        assertTrue(TestUnitRelations.hostile(bId,b,true,false,aId,a,false,false));
        assertFalse(TestUnitRelations.hostile(aId,a,false,false,bId,null,false,false));
    }
    @Test void rejectsInvalidTeamsMasksAndUnboundedOverrides() {
        assertThrows(IllegalArgumentException.class, () -> unit(4,0,false,false,Map.of()));
        assertThrows(IllegalArgumentException.class, () -> unit(0,16,false,false,Map.of()));
        var pairs = new java.util.HashMap<UUID,Boolean>(); for(int i=0;i<65;i++)pairs.put(UUID.randomUUID(),true);
        assertThrows(IllegalArgumentException.class, () -> unit(0,0,false,false,pairs));
    }
}
