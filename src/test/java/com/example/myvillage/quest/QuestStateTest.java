package com.example.myvillage.quest;

import com.example.myvillage.wilderness.WildernessRules;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestStateTest {
    private QuestState.Instance issue(QuestState state,String map,String region,UUID player){
        var definition=QuestMap.get(map);var source=WildernessRules.bundled().region(region).orElseThrow();
        return state.issue(player,map,"board_"+region,"签发地",region,723,definition.realmPool().equals("rogue")?source.rogue():source.beast(),source.encounterMax(),definition,QuestRules.get().hash(map));
    }
    @Test void eightIssuingRegionsFreezeEveryGuardAndBossThroughReload(){
        for(String map:QuestRules.get().maps())for(String region:WildernessRules.bundled().regions().keySet()){
            var state=new QuestState();var r=issue(state,map,region,UUID.randomUUID());
            var rules=WildernessRules.bundled().region(region).orElseThrow();
            var definition=QuestMap.get(map);
            var range=definition.realmPool().equals("rogue")?rules.rogue():rules.beast();
            var restored=QuestState.decode(state.encode()).instances.get(r.id);
            assertEquals(region,restored.region);assertEquals(r.sourceName,restored.sourceName);
            for(int i=0;i<r.slots.size();i++){assertEquals(r.slots.get(i).id,restored.slots.get(i).id);assertEquals(r.slots.get(i).key(),restored.slots.get(i).key());}
            for(var slot:r.slots){
                var group=definition.groups().stream().filter(g->g.id().equals(slot.group)).findFirst().orElseThrow();
                if(group.actors().get(slot.index).boss())assertEquals(rules.encounterMax(),slot.key());
                else assertTrue(range.contains(slot.key()));
            }
        }
    }
    @Test void onlyBoundSpawnedDeathsCompleteRequiredGroups(){
        var state=new QuestState();var r=issue(state,"beast_den","ximo",UUID.randomUUID());var map=QuestMap.get(r.map);
        assertFalse(state.killed(r.id,UUID.randomUUID()));assertFalse(state.killed(r.id,r.slots.getFirst().id));assertFalse(state.goal(r,map));
        for(var s:r.slots)if(map.groups().stream().anyMatch(g->g.required()&&g.id().equals(s.group))){s.spawned=true;assertTrue(state.killed(r.id,s.id));assertFalse(state.killed(r.id,s.id));}
        assertTrue(state.goal(r,map));assertTrue(r.slots.stream().filter(s->s.group.equals("side_optional")).noneMatch(s->s.dead));
        var restored=QuestState.decode(state.encode());assertTrue(restored.goal(restored.instances.get(r.id),map));
    }
    @Test void aPlayerCannotReplaceAnActiveRunOrPendingReceipt(){
        var state=new QuestState();var p=UUID.randomUUID();var r=issue(state,"bandit_stronghold","ximo",p);
        assertThrows(IllegalArgumentException.class,()->issue(state,"beast_den","zhongzhou",p));
        var m=state.members.get(p);m.result="SUCCESS";m.pendingReward=true;state.instances.remove(r.id);
        assertThrows(IllegalArgumentException.class,()->issue(state,"beast_den","zhongzhou",p));
        m.pendingReward=false;m.claimed=true;assertDoesNotThrow(()->issue(state,"beast_den","zhongzhou",p));
    }
    @Test void incompleteRosterAndForgedSourceAssignmentsFailClosed(){
        var state=new QuestState();var r=issue(state,"bandit_stronghold","ximo",UUID.randomUUID());
        var slot=r.slots.removeFirst();assertThrows(IllegalStateException.class,state::validate);r.slots.addFirst(slot);
        slot.stage=8;assertThrows(IllegalStateException.class,state::validate);slot.stage=0;
        r.party.clear();assertThrows(IllegalStateException.class,state::validate);
    }
    @Test void reservedDimensionsCannotBecomeExternalReturnLocations(){
        var state=new QuestState();var p=UUID.randomUUID();var r=issue(state,"beast_den","ximo",p);
        state.members.get(p).outside=new QuestState.Place(QuestState.dimension(r.id),1,80,1,0,0);
        assertThrows(IllegalStateException.class,state::validate);
        assertEquals(r.id,QuestState.identity(QuestState.dimension(r.id)).orElseThrow());assertTrue(QuestState.reserved("yuanxian:expedition_template"));
        assertTrue(QuestState.identity("yuanxian:expedition/not-a-uuid").isEmpty());assertFalse(QuestState.reserved("yuanxian:qianzhen/"+p));
    }
}
