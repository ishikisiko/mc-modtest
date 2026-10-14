package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.wilderness.WildernessRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WildAvatarPlannerTest {
    private static PersonView person(int id, boolean alive, int sect, String rank, String status, String region, String realm, int stage) {
        return new PersonView(id,"person"+id,"","male",alive,-100,-1,"",-1,List.of(2000,2000,2000,2000,2000),"five",realm,stage,0,
                sect,"",rank,-1,region,status,"","",0,List.of(50,50,50,50,50),List.of());
    }
    private static PersonView rogue(int id) { return person(id,true,-1,"rogue","travelling","ximo","qi_refining",1); }
    @Test void onlyLocalLivingNonsecludedUnsectedRoguesInTheRangeAreCandidates() {
        var range = WildernessRules.bundled().region("ximo").orElseThrow().rogue();
        List<PersonView> input = List.of(rogue(1),rogue(1),
                person(2,false,-1,"rogue","travelling","ximo","qi_refining",1),
                person(3,true,7,"rogue","travelling","ximo","qi_refining",1),
                person(4,true,-1,"outer","travelling","ximo","qi_refining",1),
                person(5,true,-1,"rogue","secluded","ximo","qi_refining",1),
                person(6,true,-1,"rogue","travelling","donghai","qi_refining",1),
                person(7,true,-1,"rogue","travelling","ximo","qi_refining",8),
                person(8,true,-1,"rogue","travelling","ximo","fake",0));
        assertEquals(List.of(1),WildAvatarPlanner.candidates(input,"ximo",range,77).stream().map(PersonView::id).toList());
        assertTrue(WildAvatarPlanner.candidates(List.of(),"ximo",range,77).isEmpty());
    }
    @Test void orderIsIndependentOfLedgerIterationAndSharedCapsPreventDuplicatesAcrossPlayers() {
        var range = WildernessRules.bundled().region("ximo").orElseThrow().rogue();
        List<PersonView> input = new ArrayList<>(); for(int id=0;id<20;id++) input.add(rogue(id));
        List<PersonView> ordered = WildAvatarPlanner.candidates(input,"ximo",range,55);
        java.util.Collections.reverse(input);
        assertEquals(ordered,WildAvatarPlanner.candidates(input,"ximo",range,55));
        Set<Integer> represented = new HashSet<>(Set.of(ordered.getFirst().id()));
        List<PersonView> firstPlayer = WildAvatarPlanner.select(ordered,represented,0,0,represented.size(),3,12,4);
        assertEquals(3,firstPlayer.size());
        firstPlayer.forEach(p -> assertTrue(represented.add(p.id())));
        assertTrue(WildAvatarPlanner.select(ordered,represented,3,3,represented.size(),3,12,4).isEmpty());
        assertTrue(WildAvatarPlanner.select(ordered,represented,0,12,represented.size(),3,12,40).isEmpty());
        assertEquals(1,WildAvatarPlanner.select(ordered,Set.of(),2,5,7,3,12,8).size());
    }
    @Test void safePlacementColumnsAreStableBoundedAndHaveNoDayOrActiveChunkInput() {
        var settings = WildernessRules.bundled().rogues();
        boolean found = false;
        for (int person = 0; person < 100; person++) {
            List<WildAvatarPlanner.Column> columns = WildAvatarPlanner.columns(77,person,-40,72,settings);
            assertEquals(columns,WildAvatarPlanner.columns(77,person,-40,72,settings));
            assertTrue(columns.size()<=settings.probesPerPerson());
            assertEquals(columns.size(),new HashSet<>(columns).size());
            for(var c:columns) {
                found=true; long dx=c.x()+40,dz=c.z()-72,d=dx*dx+dz*dz;
                assertTrue(d>=settings.minDistance()*settings.minDistance());
                assertTrue(d<=settings.spawnRadius()*settings.spawnRadius());
            }
        }
        assertTrue(found);
    }
    @Test void eachLedgerChangeWithdrawsEligibilityWithoutInventingAReplacement() {
        var range=WildernessRules.bundled().region("ximo").orElseThrow().rogue();
        assertTrue(WildAvatarPlanner.eligible(rogue(1),"ximo",range));
        for(PersonView changed:List.of(
                person(1,false,-1,"rogue","travelling","ximo","qi_refining",1),
                person(1,true,8,"outer","at_sect","ximo","qi_refining",1),
                person(1,true,-1,"rogue","travelling","donghai","qi_refining",1),
                person(1,true,-1,"rogue","travelling","ximo","foundation_establishment",0))) {
            assertFalse(WildAvatarPlanner.eligible(changed,"ximo",range));
        }
    }
    @Test void blockedFirstCandidatesCannotStarveLaterPeopleWithOneProbePerPass() {
        var range = WildernessRules.bundled().region("ximo").orElseThrow().rogue();
        List<PersonView> ordered = WildAvatarPlanner.candidates(List.of(rogue(1), rogue(2), rogue(3), rogue(4), rogue(5)), "ximo", range, 55);
        Set<Integer> permanentlyBlocked = Set.of(ordered.get(0).id(), ordered.get(1).id(), ordered.get(2).id());
        Set<Integer> existing = new HashSet<>(Set.of(777)); // an existing sect avatar retains its identity and slot
        List<Integer> attempted = new ArrayList<>();
        for (int cursor = 0; cursor < ordered.size(); cursor++) {
            List<PersonView> selected = WildAvatarPlanner.select(ordered, existing, 0, 0, 1, 3, 12, 4, cursor);
            assertEquals(selected, WildAvatarPlanner.select(ordered, existing, 0, 0, 1, 3, 12, 4, cursor));
            assertTrue(selected.size() <= 3);
            PersonView first = selected.getFirst(); // a one-probe pass cannot reach the second candidate
            attempted.add(first.id());
            if (!permanentlyBlocked.contains(first.id())) { existing.add(first.id()); break; }
        }
        assertEquals(4, attempted.size());
        assertEquals(ordered.get(3).id(), attempted.getLast());
        assertEquals(Set.of(777, ordered.get(3).id()), existing);
        assertTrue(WildAvatarPlanner.select(ordered, existing, 3, 3, 4, 3, 12, 4, 19).isEmpty());
        assertTrue(WildAvatarPlanner.select(ordered, existing, 0, 12, 2, 3, 12, 4, 19).isEmpty());
        assertTrue(WildAvatarPlanner.select(ordered, existing, 0, 1, 4, 3, 12, 4, 19).isEmpty());
        assertFalse(WildAvatarPlanner.select(ordered, existing, 1, 1, 2, 3, 12, 4, 19).stream().anyMatch(p -> existing.contains(p.id())));
    }
}
