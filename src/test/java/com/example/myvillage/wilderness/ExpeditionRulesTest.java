package com.example.myvillage.wilderness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.example.myvillage.alchemy.MedicineState;
import com.example.myvillage.forging.ForgeMark;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;

class ExpeditionRulesTest {
    private JsonObject data(){return JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(ExpeditionRules.RESOURCE))).getAsJsonObject();}
    @Test void malformedEncounterCannotSilentlyLoad(){var d=data();d.addProperty("guard_fraction",1);assertThrows(IllegalArgumentException.class,()->new ExpeditionRules(d));var duplicate=data();duplicate.getAsJsonArray("mobs").add(duplicate.getAsJsonArray("mobs").get(0));assertThrows(IllegalArgumentException.class,()->new ExpeditionRules(duplicate));}
    @Test void bodyScaleMustRemainFiniteAndBounded(){
        for(double value:new double[]{0,-1,4,Double.NaN}){
            var d=data();d.getAsJsonArray("mobs").get(2).getAsJsonObject().addProperty("body_scale",value);
            assertThrows(IllegalArgumentException.class,()->new ExpeditionRules(d));
        }
    }
    @Test void eachMedicineExpiresWithoutOfflineAdvancementOrStacking(){
        var r=new ExpeditionRules(data());
        for(String id:r.ids()){
            String pill=r.mob(id).get("pill_id").getAsString();int ticks=r.medicine(pill).duration();
            var s=MedicineState.EMPTY.begin(pill,600,ticks,3);assertThrows(IllegalStateException.class,()->s.begin(pill,600,ticks));
            var current=s;for(int i=0;i<ticks;i++)current=current.tick(20).next();
            assertEquals("",current.effect());assertEquals(1,current.quality());assertEquals(Math.max(0,600-ticks),current.cooldownTicks());
        }
    }
    @Test void legacyAndNewMarksRoundTripWithNoNumericRegistryIds(){for(String id:ForgeMark.TRAITS){var mark=new ForgeMark(id,3);var encoded=ForgeMark.CODEC.encodeStart(JsonOps.INSTANCE,mark).getOrThrow();assertEquals(mark,ForgeMark.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());}assertThrows(IllegalArgumentException.class,()->new ForgeMark("unknown",1));}
}
