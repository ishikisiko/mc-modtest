package com.example.myvillage.ruins;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleProgressTest {
    BattleProgress fresh(){return new BattleProgress(List.of(2,1),"READY",0,List.of(),List.of());}
    @Test void noUnlockBeforeEveryBoundGuardianActuallyDies(){
        var p=fresh();assertFalse(p.reward());assertFalse(p.advance());assertTrue(p.start());assertFalse(p.start());
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();p.bind(0,a);p.bind(1,b);
        assertFalse(p.died(UUID.randomUUID()));assertFalse(p.advance());
        assertTrue(p.died(a));assertFalse(p.died(a));assertFalse(p.advance());
        p.died(b);assertTrue(p.advance());assertEquals(1,p.wave());assertFalse(p.reward());assertFalse(p.advance());
        UUID c=UUID.randomUUID();p.bind(0,c);p.died(c);assertTrue(p.advance());assertTrue(p.reward());assertFalse(p.reward());assertFalse(p.advance());
    }
    @Test void peacefulRemovalRetriesOnlyTheUndefeatedSlot(){
        var p=fresh();p.start();UUID a=UUID.randomUUID(),b=UUID.randomUUID();p.bind(0,a);p.bind(1,b);p.died(a);
        p.retry(0);p.retry(1);assertTrue(p.dead(0));assertEquals(a,p.guard(0));assertNull(p.guard(1));assertFalse(p.advance());
        UUID replacement=UUID.randomUUID();p.bind(1,replacement);assertFalse(p.died(b));p.died(replacement);assertTrue(p.advance());
    }
    @Test void restartKeepsPartiallyDefeatedWaveAndDoesNotRefillCompletedCache(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var p=new BattleProgress(List.of(2,1),"ACTIVE",0,Arrays.asList(a,b),List.of(true,false));
        assertTrue(p.dead(0));assertFalse(p.advance());assertEquals(b,p.guard(1));p.died(b);assertTrue(p.advance());
        var done=new BattleProgress(List.of(2,1),"REWARDED",2,List.of(),List.of());assertEquals(BattleProgress.State.REWARDED,done.state());assertFalse(done.reward());
    }
    @Test void invalidSavesFailClosed(){
        UUID a=UUID.randomUUID();
        for(var p:List.of(new BattleProgress(List.of(2),"",0,List.of(),List.of()),
            new BattleProgress(List.of(2),"REWARDED",0,List.of(),List.of()),
            new BattleProgress(List.of(2),"ACTIVE",4,List.of(),List.of()),
            new BattleProgress(List.of(2),"ACTIVE",0,Arrays.asList(a,a),List.of(false,false)),
            new BattleProgress(List.of(2),"ACTIVE",0,Arrays.asList(a,null),List.of(false,true)))){
            assertEquals(BattleProgress.State.DORMANT,p.state());assertFalse(p.start());assertFalse(p.reward());
        }
    }
    private JsonObject json(String id)throws Exception{
        try(var in=getClass().getResourceAsStream("/data/myvillage/ruins/"+id+".json")){
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void bothBundledEncountersAndBounds()throws Exception{
        assertEquals("route",BattleRules.parse(json("duange_ruin")).mode());
        var temple=BattleRules.parse(json("fuyao_ruin"));assertEquals(3,temple.waves().size());assertEquals("myvillage:liuyun_bu",temple.manual());
        var broken=json("fuyao_ruin");broken.addProperty("tick_interval",0);assertThrows(IllegalArgumentException.class,()->BattleRules.parse(broken));
        var fractional=json("duange_ruin");fractional.addProperty("wave_delay_ticks",1.5);assertThrows(ArithmeticException.class,()->BattleRules.parse(fractional));
    }
}
