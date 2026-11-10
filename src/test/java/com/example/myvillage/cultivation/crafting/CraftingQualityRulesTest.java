package com.example.myvillage.cultivation.crafting;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingQualityRulesTest {
    private final CraftingQualityRules rules=CraftingQualityRules.get();
    private static final String QI="myvillage:qi_refining",FOUNDATION="myvillage:foundation_establishment";
    private String source()throws Exception{try(var in=getClass().getResourceAsStream(CraftingQualityRules.RESOURCE)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test void approvedThresholdsAndEveryDifficultyBoundaryAreExact(){
        assertEquals(List.of(20.0,40.0,70.0,90.0),rules.thresholds(QI,0));
        for(var tier:rules.tiers()){
            if(!tier.id().equals(QI))assertEquals(List.of(-20.0,20.0,60.0,80.0),rules.thresholds(tier.id(),0));
            for(int difficulty=0;difficulty<=3;difficulty++){
                var cuts=rules.thresholds(tier.id(),difficulty);
                for(int i=0;i<4;i++){
                    assertEquals(tier.thresholds().get(i)+difficulty*10,cuts.get(i));
                    assertEquals(i,rules.classify(tier.id(),difficulty,Math.nextDown(cuts.get(i))));
                    assertEquals(i+1,rules.classify(tier.id(),difficulty,cuts.get(i)));
                }
            }
        }
    }
    @Test void aptitudeSixtyCanProduceEveryQualityAndFailureWithoutACeiling(){
        assertEquals(0,rules.roll(60,QI,0,()->-4).quality());
        assertEquals(1,rules.roll(60,QI,0,()->-2).quality());
        assertEquals(2,rules.roll(60,QI,0,()->0).quality());
        assertEquals(3,rules.roll(60,QI,0,()->1).quality());
        assertEquals(4,rules.roll(60,QI,0,()->3).quality());
        var calls=new AtomicInteger();
        rules.roll(60,QI,0,()->{calls.incrementAndGet();return 0;});
        assertEquals(1,calls.get());
    }
    @Test void stageBaselineAndSpanNormalizeAptitudeWithoutClampingAtOneHundred(){
        assertEquals(60,rules.tier(FOUNDATION).mean(160));
        assertEquals(4,rules.roll(Integer.MAX_VALUE,QI,0,()->0).quality());
        assertTrue(rules.tier(QI).mean(Integer.MAX_VALUE)>100);
        assertEquals(0,rules.initialMin());assertEquals(60,rules.initialMax());assertEquals(12,rules.deviation());
    }
    @Test void analyticalProbabilitiesMatchIndependentReferenceAndSumToOne(){
        double[] qi={.0004290603331968383,.0473612919396179,.749881266275163,.196118715967985,.00620966532577614};
        double[] foundation={1.3083924686053e-11,.000429060320112914,.499570939666803,.452209647727185,.047790352272815};
        var a=rules.probabilities(60,QI,0);var b=rules.probabilities(160,FOUNDATION,0);
        for(int i=0;i<5;i++){assertEquals(qi[i],a.get(i),2e-7);assertEquals(foundation[i],b.get(i),2e-7);}
        for(var tier:rules.tiers())for(int difficulty=0;difficulty<=3;difficulty++)for(int aptitude:new int[]{0,20,60,100,160,300,1000,Integer.MAX_VALUE}){
            var p=rules.probabilities(aptitude,tier.id(),difficulty);
            assertEquals(1,p.stream().mapToDouble(Double::doubleValue).sum(),2e-7);
            assertTrue(p.stream().allMatch(v->v>=0&&v<=1));
        }
        assertEquals(.5,rules.probabilities(20,QI,0).get(0),1e-12);
        assertEquals(.0477903522728147,rules.probabilities(100,FOUNDATION,0).get(0),2e-7);
    }
    @Test void higherDifficultyRaisesFailureAndReducesPerfectChance(){
        var easy=rules.probabilities(60,QI,0);var hard=rules.probabilities(60,QI,3);
        assertTrue(hard.get(0)>easy.get(0));assertTrue(hard.get(4)<easy.get(4));
        assertThrows(IllegalArgumentException.class,()->rules.thresholds(QI,-1));
        assertThrows(IllegalArgumentException.class,()->rules.thresholds(QI,4));
        assertThrows(IllegalArgumentException.class,()->rules.roll(-1,QI,0,()->0));
        assertThrows(IllegalArgumentException.class,()->rules.roll(1,QI,0,()->Double.NaN));
        assertThrows(IllegalArgumentException.class,()->rules.roll(1,QI,0,()->Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->rules.tier("myvillage:missing"));
    }
    @Test void schemaRejectsUnknownDuplicateFractionalAndUnsafeParameters()throws Exception{
        for(String field:List.of("initial_roll","tiers","standard_deviation")){
            var j=JsonParser.parseString(source()).getAsJsonObject();j.remove(field);
            assertThrows(IllegalArgumentException.class,()->CraftingQualityRules.parse(new StringReader(j.toString())));
        }
        for(JsonElement value:List.of(new JsonPrimitive(0),new JsonPrimitive(-1),new JsonPrimitive("12"),new JsonPrimitive(true))){
            var j=JsonParser.parseString(source()).getAsJsonObject();j.add("standard_deviation",value);
            assertThrows(IllegalArgumentException.class,()->CraftingQualityRules.parse(new StringReader(j.toString())));
        }
        var duplicate=source().replaceFirst("\\{","{\"version\":1,");
        assertThrows(IllegalArgumentException.class,()->CraftingQualityRules.parse(new StringReader(duplicate)));
        var j=JsonParser.parseString(source()).getAsJsonObject();j.getAsJsonArray("tiers").get(0).getAsJsonObject().addProperty("span",0);
        assertThrows(IllegalArgumentException.class,()->CraftingQualityRules.parse(new StringReader(j.toString())));
        for(JsonElement value:List.of(new JsonPrimitive("20"),new JsonPrimitive(true),JsonNull.INSTANCE)){
            var bad=JsonParser.parseString(source()).getAsJsonObject();
            bad.getAsJsonArray("tiers").get(0).getAsJsonObject().getAsJsonArray("thresholds").set(0,value);
            assertThrows(IllegalArgumentException.class,()->CraftingQualityRules.parse(new StringReader(bad.toString())));
        }
    }
}
