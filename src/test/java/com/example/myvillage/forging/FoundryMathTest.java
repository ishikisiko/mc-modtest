package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundryMathTest {
    @Test void strongerHammersAndSkillImproveBothProgressAndEarnedWork(){
        var rules=FoundryRules.get();int power=0,quality=0;
        for(var hammer:rules.hammers()){
            var contribution=FormingMath.contribution(10000,hammer,0,rules.heat().ideal(),rules);
            assertTrue(contribution.points()>power);assertTrue(contribution.quality()>quality);
            var skilled=FormingMath.contribution(10000,hammer,rules.skill().max_level(),rules.heat().ideal(),rules);
            assertTrue(skilled.points()>contribution.points());assertTrue(skilled.quality()>contribution.quality());
            power=contribution.points();quality=contribution.quality();
        }
    }
    @Test void coldWorkCannotAdvanceAndLastContactCreditsOnlyRemainingWork(){
        var rules=FoundryRules.get();var hammer=rules.hammers().getLast();
        assertEquals(0,FormingMath.contribution(100,hammer,100,rules.heat().minimum()-1,rules).points());
        assertEquals(3,FormingMath.contribution(3,hammer,100,rules.heat().capacity(),rules).points());
        assertEquals(0,FormingMath.contribution(0,hammer,100,rules.heat().capacity(),rules).points());
    }
    @Test void craftsmanshipDependsOnActualWorkRatherThanNumberOfClicks(){
        var one=ForgeCraft.EMPTY.work(100,6500);var many=ForgeCraft.EMPTY;
        for(int i=0;i<100;i++)many=many.work(1,6500);
        assertEquals(one,many);assertEquals(.65,one.manualStrength(100),.000001);
        var mixed=ForgeCraft.EMPTY.work(25,2000).work(75,8000);
        assertEquals(.65,mixed.manualStrength(100),.000001);
        assertThrows(IllegalArgumentException.class,()->ForgeCraft.EMPTY.work(0,5000));
        assertThrows(IllegalArgumentException.class,()->ForgeCraft.EMPTY.work(1,10001));
    }
    @Test void recipesHaveIndependentFiniteWorkAndLargeHitsCannotExceedTheirTargets(){
        var rules=WorkshopRules.get();assertTrue(rules.craft("xuantie_chestplate").forming_points()>rules.craft("forged_shovel").forming_points());
        for(var craft:rules.crafts()){
            var blank=new Workpiece(craft.recipe(),0,0,0).work(Integer.MAX_VALUE,rules);
            assertEquals(craft.forming_points(),blank.points());assertEquals(1,blank.stage());
            assertThrows(IllegalStateException.class,()->blank.work(1,rules));
        }
    }
    @Test void overleveledRecipesHaveLowerButNonzeroLearningAndLevelsAreBounded(){
        var rules=FoundryRules.get().skill();
        assertTrue(FormingMath.experience(100,1,80,rules)<FormingMath.experience(100,1,0,rules));
        int one=FormingMath.experience(0,120,1,80,rules),many=0;
        for(int i=0;i<120;i++)many+=FormingMath.experience(i,i+1,1,80,rules);
        assertTrue(one>0);assertEquals(one,many,"Learning must not depend on strike count or rounding");
        assertEquals(rules.max_level(),FormingMath.level(Integer.MAX_VALUE,rules));
        assertEquals(0,FormingMath.level(0,rules));
    }
    @Test void configurationRejectsFractionalCostsUnknownFieldsAndZeroEffectIntervals() throws Exception {
        String source;try(var in=getClass().getResourceAsStream("/data/myvillage/forging/foundry.json")){source=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        for(String field:new String[]{"fuel_ticks","capacity","temper_ticks"}){
            var json=JsonParser.parseString(source).getAsJsonObject();json.getAsJsonObject("heat").addProperty(field,0);
            assertThrows(RuntimeException.class,()->FoundryRules.parse(new StringReader(json.toString())));
        }
        var fractional=JsonParser.parseString(source).getAsJsonObject();fractional.getAsJsonArray("hammers").get(0).getAsJsonObject().addProperty("power",1.5);
        assertThrows(RuntimeException.class,()->FoundryRules.parse(new StringReader(fractional.toString())));
        var unknown=JsonParser.parseString(source).getAsJsonObject();unknown.getAsJsonObject("heat").addProperty("free_heat",1000);
        assertThrows(RuntimeException.class,()->FoundryRules.parse(new StringReader(unknown.toString())));
        var invalid=JsonParser.parseString(source).getAsJsonObject();invalid.getAsJsonArray("handles").get(2).getAsJsonObject().addProperty("effect_ticks",0);
        assertThrows(RuntimeException.class,()->FoundryRules.parse(new StringReader(invalid.toString())));
    }
}
