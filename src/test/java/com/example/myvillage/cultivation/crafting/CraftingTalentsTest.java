package com.example.myvillage.cultivation.crafting;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.cultivation.fate.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingTalentsTest {
    private static Map<String,FateDefinition> definitions;
    @BeforeAll static void setup()throws Exception{
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();var values=new TreeMap<String,FateDefinition>();
        for(String id:List.of("alchemy_prodigy","forging_prodigy","clod"))try(var in=CraftingTalentsTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/"+id+".json")){
            assertNotNull(in);values.put("myvillage:"+id,FateDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new InputStreamReader(in))).getOrThrow());
        }definitions=Map.copyOf(values);
    }
    private static List<Map<String,Double>> effects(){return definitions.values().stream().map(FateDefinition::effects).toList();}
    @Test void threeTalentsFitTogetherAndKeepSeparateOpeningOverrides(){
        assertEquals("",FateSelection.validate(new ArrayList<>(definitions.keySet()),definitions,8,true));
        assertEquals(7,definitions.values().stream().mapToInt(FateDefinition::cost).sum());
        assertTrue(definitions.values().stream().noneMatch(FateDefinition::npcEligible));
        var selection=CraftingTalents.selection(new ArrayList<>(definitions.keySet()),effects());
        assertEquals(60,selection.alchemyOverride());assertEquals(60,selection.forgingOverride());
        var profile=CraftingTalents.modifier(effects());assertEquals(-5,profile.meanShift());assertEquals(4,profile.varianceMultiplier());
        var rendered=definitions.values().stream().flatMap(d->com.example.myvillage.client.cultivation.panel.FatePresentation.description(d).stream()).map(c->c.getString()).toList();
        assertTrue(rendered.stream().anyMatch(s->s.endsWith("60 点")));assertTrue(rendered.stream().anyMatch(s->s.endsWith("×4")));assertTrue(rendered.stream().anyMatch(s->s.endsWith("-5")));
    }
    @Test void approvedClodCurveUsesMeanMinusFiveAndVarianceFourTimesAtEveryTier(){
        var rules=CraftingQualityRules.get();var modifier=CraftingTalents.modifier(effects());
        for(var tier:rules.tiers()){
            int aptitude=tier.baseline()+60;
            assertEquals(55,rules.roll(aptitude,tier.id(),0,modifier,()->0).score());
            assertEquals(79,rules.roll(aptitude,tier.id(),0,modifier,()->1).score());
            assertEquals(31,rules.roll(aptitude,tier.id(),0,modifier,()->-1).score());
        }
        var p=rules.probabilities(60,"myvillage:qi_refining",0,modifier);
        double[] expected={.072374341,.193611190,.468028938,.193611190,.072374341};
        for(int i=0;i<5;i++)assertEquals(expected[i],p.get(i),2e-7);
        assertEquals(CraftingQualityRules.Modifier.NORMAL,CraftingTalents.modifier(List.of()));
    }
    @Test void eachModifiedPillRemainsIndependentAndItsFrozenOutcomeSurvivesSave(){
        int[] draws={0};var recipe=AlchemyRules.get().recipe("huiling").orElseThrow();
        var batch=AlchemyBatch.create(UUID.randomUUID(),recipe,60,CraftingTalents.modifier(effects()),()->draws[0]++==0?-2:2);
        assertEquals(List.of(0,4),batch.outcomes().stream().map(CraftingQualityRules.Roll::quality).toList());assertEquals(2,draws[0]);
        assertEquals(batch,AlchemyBatch.read(batch.save()).orElseThrow());assertEquals(7,batch.outcomes().getFirst().score());assertEquals(103,batch.outcomes().getLast().score());
    }
    @Test void malformedNewEffectTypesAndIncompleteModifiersAreRejected(){
        for(var map:List.of(Map.of(CraftingTalents.ALCHEMY,-1.0),Map.of(CraftingTalents.ALCHEMY,60.5),Map.of(CraftingTalents.MEAN,-5.0),
                Map.of(CraftingTalents.MEAN,-5.0,CraftingTalents.VARIANCE,0.0),Map.of(CraftingTalents.MEAN,Double.NaN,CraftingTalents.VARIANCE,4.0)))
            assertThrows(IllegalArgumentException.class,()->new FateDefinition("name","description",0,1,"group",true,map,Optional.empty(),0,5));
    }
}
