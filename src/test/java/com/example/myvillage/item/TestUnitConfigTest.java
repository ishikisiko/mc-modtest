package com.example.myvillage.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestUnitConfigTest {
    private TestUnitConfig config(List<String> fates, Map<String,Integer> roots, Map<String,Double> attrs) {
        return new TestUnitConfig("myvillage:cultivator","测试 NPC","myvillage:qi_refining",2,4,3,fates,roots,attrs,
                100,1000,true,new TestUnitRelations.Unit(UUID.randomUUID(),0,2,true,false,Map.of()));
    }
    @Test void roundTripRetainsIdentityQualitiesAptitudesAndPrecision() {
        var value=config(List.of("myvillage:a","myvillage:b","myvillage:c"),Map.of("fire",350,"sword",900),
                Map.of("minecraft:generic.movement_speed",0.375,"myvillage:body_defense",33.5));
        assertEquals(value,TestUnitConfig.parse(value.json()));
    }
    @Test void randomSelectionIsExplicitAndPartialOrDuplicateSelectionsAreRejected() {
        assertTrue(config(List.of(),Map.of(),Map.of()).fates().isEmpty());
        assertThrows(IllegalArgumentException.class,()->config(List.of("myvillage:a"),Map.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->config(List.of("myvillage:a","myvillage:a","myvillage:c"),Map.of(),Map.of()));
    }
    @Test void rejectsNegativeOrUnknownAptitudesAndNonfiniteAttributes() {
        assertThrows(IllegalArgumentException.class,()->config(List.of(),Map.of("fire",-1),Map.of()));
        assertThrows(IllegalArgumentException.class,()->config(List.of(),Map.of("invented",1),Map.of()));
        assertThrows(IllegalArgumentException.class,()->config(List.of(),Map.of(),Map.of("a",Double.NaN)));
        assertThrows(IllegalArgumentException.class,()->config(List.of(),Map.of(),Map.of("a",Double.POSITIVE_INFINITY)));
    }
    @Test void rejectsUnknownRealmStageNullAndOversizedPackets() {
        var json=config(List.of(),Map.of(),Map.of()).json();
        assertThrows(RuntimeException.class,()->TestUnitConfig.parse(json.replace("qi_refining","invented")));
        assertThrows(RuntimeException.class,()->TestUnitConfig.parse(json.replace("\"stage\":2","\"stage\":999")));
        assertThrows(IllegalArgumentException.class,()->TestUnitConfig.parse("null"));
        assertThrows(IllegalArgumentException.class,()->TestUnitConfig.parse(" ".repeat(TestUnitConfig.MAX_JSON+1)));
    }
}
