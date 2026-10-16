package com.example.myvillage.ruins;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DemonTrialRulesTest {
    private JsonObject json()throws Exception{
        try(var in=getClass().getResourceAsStream("/data/myvillage/ruins/demon_trial.json")){
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void portableTrialHasThreeDistinctWavesAndDoesNotMintManuals()throws Exception{
        var root=json();var summon=DemonTrialRules.parse(root);var battle=BattleRules.parse(root);
        assertTrue(battle.manual().isEmpty());assertEquals(3,battle.waves().size());
        assertEquals(2,battle.waves().getFirst().guards().size());
        assertTrue(battle.waves().getLast().guards().stream().anyMatch(BattleRules.Guard::elite));
        assertTrue(summon.separation()>=2*summon.radius());assertFalse(battle.rewards().isEmpty());
    }
    @Test void invalidGeometryCannotConsumePearlsIntoAnImpossibleTrial()throws Exception{
        var root=json();root.getAsJsonObject("summoning").addProperty("clear_radius",2);
        assertThrows(IllegalArgumentException.class,()->DemonTrialRules.parse(root));
        var blocked=json();blocked.getAsJsonArray("waves").get(0).getAsJsonObject().getAsJsonArray("guards").get(0).getAsJsonObject().add("pos",blocked.get("cache"));
        assertThrows(IllegalArgumentException.class,()->DemonTrialRules.parse(blocked));
        var elevated=json();elevated.getAsJsonArray("waves").get(0).getAsJsonObject().getAsJsonArray("guards").get(0).getAsJsonObject().getAsJsonArray("pos").set(1,new JsonPrimitive(1));
        assertThrows(IllegalArgumentException.class,()->DemonTrialRules.parse(elevated));
    }
    @Test void summoningLimitsAreBoundedIntegers()throws Exception{
        var root=json();root.getAsJsonObject("summoning").addProperty("separation",100000);
        assertThrows(IllegalArgumentException.class,()->DemonTrialRules.parse(root));
        var fractional=json();fractional.getAsJsonObject("summoning").addProperty("stack_size",1.5);
        assertThrows(ArithmeticException.class,()->DemonTrialRules.parse(fractional));
    }
}
