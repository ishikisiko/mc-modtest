package com.example.myvillage.cultivation.crafting;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingOpeningRulesTest {
    private String source()throws Exception{try(var in=getClass().getResourceAsStream(CraftingOpeningRules.RESOURCE)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test void hashMatchesIndependentPythonOracleAndUsesProfessionAndFateDomains(){
        var rules=CraftingOpeningRules.get();var player=UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        var ids=List.of("myvillage:robust","myvillage:lightfoot","myvillage:bright");
        int[] alchemy={5,0,-5},forging={-5,0,0};
        for(int i=0;i<3;i++){
            assertEquals(alchemy[i],rules.contribution(player,CraftingAptitudes.Profession.ALCHEMY,ids.get(i)));
            assertEquals(forging[i],rules.contribution(player,CraftingAptitudes.Profession.FORGING,ids.get(i)));
        }
        var selected=new CraftingOpeningRules.Selection(ids,-1,-1);
        assertEquals(30,rules.score(player,CraftingAptitudes.Profession.ALCHEMY,selected));
        assertEquals(25,rules.score(player,CraftingAptitudes.Profession.FORGING,selected));
        assertThrows(IllegalArgumentException.class,()->rules.contribution(player,CraftingAptitudes.Profession.TALISMAN,ids.getFirst()));
    }
    @Test void invalidSelectionsAndBucketsCannotGenerateScores(){
        var rules=CraftingOpeningRules.get();
        assertThrows(IllegalArgumentException.class,()->new CraftingOpeningRules.Selection(List.of("myvillage:a","myvillage:a","myvillage:b"),-1,-1));
        assertThrows(IllegalArgumentException.class,()->new CraftingOpeningRules.Selection(List.of("myvillage:a"),-1,-1));
        assertThrows(IllegalArgumentException.class,()->new CraftingOpeningRules.Selection(List.of(),60,-1));
        assertThrows(IllegalArgumentException.class,()->rules.deltaForBucket(-1));assertThrows(IllegalArgumentException.class,()->rules.deltaForBucket(100));
        assertThrows(IllegalArgumentException.class,()->rules.score(UUID.randomUUID(),CraftingAptitudes.Profession.ALCHEMY,CraftingOpeningRules.Selection.PENDING));
    }
    @Test void malformedAndAmbiguousConfigurationIsRejected()throws Exception{
        for(String field:List.of("version","base","hash_domain","weighted_deltas")){
            var j=JsonParser.parseString(source()).getAsJsonObject();j.remove(field);
            assertThrows(IllegalArgumentException.class,()->CraftingOpeningRules.parse(new StringReader(j.toString())));
        }
        for(var value:List.of(new JsonPrimitive(true),new JsonPrimitive("30"),new JsonPrimitive(29),new JsonPrimitive(30.5))){
            var j=JsonParser.parseString(source()).getAsJsonObject();j.add("base",value);
            assertThrows(IllegalArgumentException.class,()->CraftingOpeningRules.parse(new StringReader(j.toString())));
        }
        var duplicate=source().replaceFirst("\\{","{\"base\":30,");
        assertThrows(IllegalArgumentException.class,()->CraftingOpeningRules.parse(new StringReader(duplicate)));
        var zero=JsonParser.parseString(source()).getAsJsonObject();zero.getAsJsonArray("weighted_deltas").get(0).getAsJsonObject().addProperty("weight",0);
        assertThrows(IllegalArgumentException.class,()->CraftingOpeningRules.parse(new StringReader(zero.toString())));
    }
}
