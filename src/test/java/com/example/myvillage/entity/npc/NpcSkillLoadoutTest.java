package com.example.myvillage.entity.npc;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcSkillLoadoutTest {
    private NpcSkillLoadout.Learned skill(String id,long points,boolean auto,int priority) {
        return new NpcSkillLoadout.Learned("myvillage:"+id,points,auto,priority);
    }
    @Test void roundTripPreservesMasteryCoreWeaponAndAutomation() {
        var state=new NpcSkillLoadout("myvillage:qingfeng_sword","myvillage:core",List.of(
                skill("core",1800,false,0),skill("jianqi_jue",1200,true,10),skill("huifeng_jianzhen",50,false,1)));
        assertEquals(state,NpcSkillLoadout.parse(state.json()));
    }
    @Test void prioritiesAreStableAndDisabledAbilitiesAreExcluded() {
        var state=new NpcSkillLoadout("","",List.of(skill("z",0,true,3),skill("b",0,true,0),skill("a",0,true,0),skill("disabled",999,false,0)));
        assertEquals(List.of("myvillage:a","myvillage:b","myvillage:z"),state.automatic().stream().map(NpcSkillLoadout.Learned::id).toList());
    }
    @Test void rejectsDuplicatesUnlearnedCoreInvalidMasteryAndPriority() {
        var row=skill("a",0,true,0);
        assertThrows(IllegalArgumentException.class,()->new NpcSkillLoadout("","",List.of(row,row)));
        assertThrows(IllegalArgumentException.class,()->new NpcSkillLoadout("","myvillage:missing",List.of(row)));
        assertThrows(IllegalArgumentException.class,()->skill("a",-1,true,0));
        assertThrows(IllegalArgumentException.class,()->skill("a",0,true,-1));
        assertThrows(IllegalArgumentException.class,()->skill("a",0,true,1000));
    }
    @Test void refusesOversizedOrMalformedWireDataAndCopiesCollections() {
        assertThrows(IllegalArgumentException.class,()->NpcSkillLoadout.parse("{"));
        assertThrows(IllegalArgumentException.class,()->NpcSkillLoadout.parse("null"));
        assertThrows(IllegalArgumentException.class,()->NpcSkillLoadout.parse(" ".repeat(32769)));
        var rows=new java.util.ArrayList<NpcSkillLoadout.Learned>();rows.add(skill("a",Long.MAX_VALUE,true,999));
        var state=new NpcSkillLoadout("","",rows);rows.clear();assertEquals(1,state.learned().size());
        assertThrows(UnsupportedOperationException.class,()->state.learned().clear());
    }
}
