package com.example.myvillage.wilderness.habitat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HabitatStateTest {
    private final HabitatRules.Kind kind=new HabitatRules.Kind("wolf_den","妖狼巢地","beast",List.of("demon_wolf","demon_wolf","demon_wolf"),1,true,"");
    @Test void clearingIsFiniteWithinMonthAndNextMonthRefreshesExactlyOnce(){
        var state=new HabitatState();var round=state.site("wolf_den_0");assertTrue(state.refresh(round,0,1,true,kind,42));
        var old=round.members.getFirst().id;assertTrue(state.death(round.site,0,old));assertFalse(state.death(round.site,0,old));assertEquals(1,round.kills);
        assertFalse(state.refresh(round,0,1,true,kind,42));assertEquals(2,round.living());assertTrue(state.refresh(round,1,1,true,kind,42));
        assertEquals(3,round.living());assertFalse(state.current(round.site,0,old));assertNotEquals(old,round.members.getFirst().id);
        assertFalse(state.refresh(round,1,1,true,kind,42));
    }
    @Test void unloadedMissingEntitiesDoNotResetSlotsAndReloadPreservesIdentity(){
        var state=new HabitatState();var round=state.site("wolf_den_0");state.refresh(round,4,1,true,kind,42);round.members.getFirst().spawned=true;
        state.death(round.site,round.generation,round.members.get(1).id);
        var copy=HabitatState.decode(state.encode());var restored=copy.site(round.site);
        assertEquals(round.members.getFirst().id,restored.members.getFirst().id);assertTrue(restored.members.getFirst().spawned);assertTrue(restored.members.get(1).dead);
        assertFalse(copy.refresh(restored,4,1,true,kind,42));
    }
    @Test void calendarRollbackAndSkippedMonthsCannotProduceExtraCycles(){
        var state=new HabitatState();var round=state.site("wolf_den_0");state.refresh(round,10,1,true,kind,42);
        assertFalse(state.refresh(round,8,1,true,kind,42));assertTrue(state.refresh(round,80,1,true,kind,42));assertEquals(1,round.generation);
        assertFalse(state.refresh(round,79,1,true,kind,42));assertFalse(state.refresh(round,80,1,true,kind,42));
    }
    @Test void playerProximityDefersRefreshAndConstructionSurvivesMonthlyRecovery(){
        var state=new HabitatState();var round=state.site("wolf_den_0");state.refresh(round,0,1,true,kind,42);
        var id=round.members.getFirst().id;assertFalse(state.refresh(round,1,1,false,kind,42));assertEquals(id,round.members.getFirst().id);
        round.construction.add(99L);assertTrue(state.refresh(round,1,1,true,kind,42));
        var copy=HabitatState.decode(state.encode());assertTrue(copy.site(round.site).occupied());
        assertTrue(round.construction.contains(99L));assertFalse(state.refresh(round,1,1,true,kind,42));
    }
    @Test void oldGenerationDeathReceiptsCannotKillNewMembers(){
        var state=new HabitatState();var round=state.site("wolf_den_0");state.refresh(round,0,1,true,kind,42);UUID old=round.members.getFirst().id;
        state.refresh(round,1,1,true,kind,42);assertFalse(state.death(round.site,0,old));assertEquals(3,round.living());
    }
    @Test void saveRejectsDuplicateIdentitiesAndWrongVersion(){
        var state=new HabitatState();var round=state.site("wolf_den_0");state.refresh(round,0,1,true,kind,42);round.members.get(1).id=round.members.getFirst().id;
        assertThrows(IllegalArgumentException.class,()->HabitatState.decode(state.encode()));
        assertThrows(IllegalArgumentException.class,()->HabitatState.decode("{\"version\":99}"));
    }
}
