package com.example.myvillage.sim.fate;

import com.example.myvillage.sim.SimRng;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcFatePoolTest {
    @Test void playerOpeningTalentsCannotEnterNpcBirthRolls(){
        var pool=NpcFatePool.bundled();var ids=Set.of("myvillage:alchemy_prodigy","myvillage:forging_prodigy","myvillage:clod");
        assertEquals(3,pool.entries().stream().filter(e->ids.contains(e.id())&&!e.enabled()).count());
        for(int i=0;i<100;i++)assertTrue(Collections.disjoint(ids,pool.roll(new int[5],false,random(i)).selected()));
    }
    private static SimRng random(int id) { return SimRng.at(42, 0, id, 114); }
    private static NpcFatePool.Entry entry(String id, int cost, String group) {
        return new NpcFatePool.Entry("myvillage:" + id, cost, group, true, "", 0, 5);
    }
    @Test void ordinaryTotalsStayBelowBudgetAndOnlyProdigiesUseAllNinePoints() {
        var pool = NpcFatePool.bundled();
        assertEquals(9, pool.budget());
        var observed = new TreeSet<Integer>();
        var definitions = new HashMap<String, NpcFatePool.Entry>();
        pool.entries().forEach(e -> definitions.put(e.id(), e));
        for (int id = 0; id < 2000; id++) for (boolean prodigy : new boolean[]{false, true}) {
            var roll = pool.roll(new int[]{300, 0, 0, 0, 0}, prodigy, random(id));
            assertEquals(3, roll.selected().size());
            assertEquals(3, roll.selected().stream().distinct().count());
            assertEquals(3, roll.selected().stream().map(definitions::get).map(NpcFatePool.Entry::group).distinct().count());
            assertEquals(roll.spent(), roll.selected().stream().map(definitions::get).mapToInt(NpcFatePool.Entry::cost).sum());
            assertTrue(roll.selected().stream().map(definitions::get).allMatch(e -> e.eligible(new int[]{300,0,0,0,0})));
            if (prodigy) assertEquals(9, roll.spent());
            else { assertTrue(roll.spent() < 9); observed.add(roll.spent()); }
        }
        assertEquals(Set.of(3,4,5,6,7,8), observed);
    }
    @Test void newEnabledEntriesAutomaticallyParticipateAndInputOrderDoesNotChangeTheRoll() {
        var entries = new ArrayList<>(NpcFatePool.bundled().entries());
        entries.add(entry("future_fate", 2, "future_group"));
        var pool = new NpcFatePool(9, entries);
        Collections.reverse(entries);
        var reversed = new NpcFatePool(9, entries);
        boolean appeared = false;
        for (int id = 0; id < 2000; id++) {
            var roll = pool.roll(new int[5], false, random(id));
            assertEquals(roll, reversed.roll(new int[5], false, random(id)));
            appeared |= roll.selected().contains("myvillage:future_fate");
        }
        assertTrue(appeared);
    }
    @Test void disabledEntriesRootGatesAndExclusiveGroupsAlwaysApply() {
        var entries = new ArrayList<>(List.of(entry("a",1,"a"), entry("b",1,"b"), entry("c",1,"c"),
                entry("d",3,"body"), entry("e",5,"body")));
        entries.add(new NpcFatePool.Entry("myvillage:disabled",1,"disabled",false,"",0,5));
        entries.add(new NpcFatePool.Entry("myvillage:root_gate",3,"root",true,"myvillage:fire",250,1));
        var pool = new NpcFatePool(9, entries);
        var blocked = pool.entries().stream().filter(e -> e.id().endsWith("root_gate")).findFirst().orElseThrow();
        assertFalse(blocked.eligible(new int[]{0,0,0,249,0}));
        assertFalse(blocked.eligible(new int[]{1,0,0,300,0}));
        assertTrue(blocked.eligible(new int[]{0,0,0,300,0}));
        for (int id=0;id<500;id++) {
            var roll=pool.roll(new int[5],false,random(id));
            assertFalse(roll.selected().contains("myvillage:disabled"));
            assertFalse(roll.selected().contains("myvillage:root_gate"));
            assertFalse(roll.selected().containsAll(List.of("myvillage:d","myvillage:e")));
        }
    }
    @Test void unavailableExactBudgetFailsWithoutProducingAnUnderfundedProdigy() {
        var pool = new NpcFatePool(9,List.of(entry("a",1,"a"),entry("b",1,"b"),entry("c",1,"c")));
        assertEquals(3,pool.roll(new int[5],false,random(1)).spent());
        assertThrows(IllegalArgumentException.class,()->pool.roll(new int[5],true,random(1)));
        assertThrows(IllegalArgumentException.class,()->new NpcFatePool(3,pool.entries()));
        assertThrows(IllegalArgumentException.class,()->new NpcFatePool(19,pool.entries()));
    }
    @Test void customBudgetRemainsIndependentOfPreviouslySavedResults() {
        var entries = List.of(entry("a",1,"a"),entry("b",1,"b"),entry("c",1,"c"),entry("d",3,"d"));
        var pool = new NpcFatePool(5,entries);
        var saved = pool.roll(new int[5],true,random(4));
        new NpcFatePool(9,entries);
        assertEquals(saved,NpcFateRoll.fromJson(saved.toJson(),true));
        assertEquals(5,saved.budget());
    }
    @Test void savedRecordRejectsMissingOrCorruptFieldsAndDoesNotResolveUnknownIds() {
        var roll=new NpcFateRoll(List.of("myvillage:future_a","myvillage:future_b","myvillage:future_c"),9,9);
        assertEquals(roll,NpcFateRoll.fromJson(roll.toJson(),true));
        assertThrows(IllegalArgumentException.class,()->roll.validateIdentity(false));
        assertThrows(IllegalArgumentException.class,()->new NpcFateRoll(List.of("myvillage:a","myvillage:a","myvillage:b"),9,3));
        var invalid=roll.toJson();invalid.addProperty("spent",8.5);
        assertThrows(ArithmeticException.class,()->NpcFateRoll.fromJson(invalid,true));
        invalid.addProperty("spent","9");
        assertThrows(IllegalArgumentException.class,()->NpcFateRoll.fromJson(invalid,true));
    }
}
