package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.qi.OpenMeridians;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundationRulesTest {
    private static CultivationProfile established(){return CultivationProfile.DEFAULT.withRealmAndStage(ResourceLocation.parse("myvillage:foundation_establishment"),ResourceLocation.parse("myvillage:foundation_early"));}
    @Test void exhaustivelyChecksAllOneHundredOutcomesAndBoundaries(){
        var r=FoundationRules.get();int[] expected={40,75,92};
        for(int q=1;q<=3;q++){int n=0;for(int draw=0;draw<100;draw++)if(r.succeeds(q,draw))n++;assertEquals(expected[q-1],n);assertTrue(r.succeeds(q,expected[q-1]-1));assertFalse(r.succeeds(q,expected[q-1]));}
        assertThrows(IllegalArgumentException.class,()->r.succeeds(4,0));assertThrows(IllegalArgumentException.class,()->r.succeeds(1,100));
        assertEquals(475,r.failedStability(950));assertEquals(475,r.failedStability(951));
    }
    @Test void repairIsMonotonicAndWorksAfterGoldenCore(){
        var r=FoundationRules.get();var low=established().withFoundationQuality(1);
        assertFalse(r.canRepair(low,1));assertTrue(r.canRepair(low,2));assertTrue(r.canRepair(low,3));
        var high=low.withFoundationQuality(3);assertFalse(r.canRepair(high,2));assertFalse(r.canRepair(high,3));assertFalse(r.canRepair(high,4));
        var golden=low.withRealmAndStage(ResourceLocation.parse("myvillage:golden_core"),ResourceLocation.parse("myvillage:golden_early"));assertTrue(r.canRepair(golden,3));
        assertFalse(r.canRepair(CultivationProfile.DEFAULT,3));
        var table=com.example.myvillage.stats.CultivationStatsRules.bundled();
        assertEquals(8,table.foundationBonus(3).health()-table.foundationBonus(1).health());
        assertEquals(40,table.foundationBonus(3).power()-table.foundationBonus(1).power());
        assertFalse(r.canRepair(low.withFoundationQuality(4),3));
    }
    @Test void savedRootAndGoldenQualitySurviveEveryCopy(){
        var p=established().withFoundationQuality(3).withCultivationProgress(500).withStability(200).withCurrentSpiritualPower(50).withGoldenCoreQuality(2);
        var json=CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow();
        assertEquals(p,CultivationProfile.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
        assertEquals(2,p.withFoundationQuality(2).withStability(1).withLearnedTechniques(p.learnedTechniques()).goldenCoreQuality());
        assertEquals(3,p.withLifespanConsumedTicks(40).withMeditationQiReserve(12).withSpiritualAffinity(30).withLearnedTechniques(p.learnedTechniques()).withActiveCoreTechnique(p.activeCoreTechnique()).foundationQuality());
        JsonObject legacy=json.deepCopy().getAsJsonObject();legacy.remove("foundation_quality");
        assertEquals(0,CultivationProfile.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().foundationQuality());
        legacy.addProperty("foundation_quality",5);assertTrue(CultivationProfile.CODEC.parse(JsonOps.INSTANCE,legacy).error().isPresent());
        assertThrows(IllegalArgumentException.class,()->p.withFoundationQuality(-1));
    }
    @Test void persistedFixedBatchesCannotClaimAnotherQualityOrInsufficientAptitude() {
        var recipe=AlchemyRules.get().recipe("zhuji_high").orElseThrow();var uuid=java.util.UUID.randomUUID();
        assertTrue(recipe.acceptsBatch(new AlchemyBatch(uuid,"zhuji_high",70,3,3)));
        assertFalse(recipe.acceptsBatch(new AlchemyBatch(uuid,"zhuji_high",100,4,4)));
        assertFalse(recipe.acceptsBatch(new AlchemyBatch(uuid,"zhuji_high",20,3,3)));
        assertFalse(recipe.acceptsBatch(new AlchemyBatch(uuid,"zhuji_high",70,3,1)));
        assertTrue(AlchemyRules.get().recipe("huiling").orElseThrow().acceptsBatch(new AlchemyBatch(uuid,"huiling",100,4,4)));
    }
    @Test void fruitAndAptitudeAnchorOutputWithNoPerfectGrade(){
        var r=AlchemyRules.get();
        String[] ids={"zhuji_low","zhuji_middle","zhuji_high","buji_middle","buji_high"};int[] scores={20,50,70,60,85},qualities={1,2,3,2,3};
        for(int i=0;i<ids.length;i++){var recipe=r.recipe(ids[i]).orElseThrow();assertEquals(scores[i],recipe.minimumAptitude());assertEquals(qualities[i],recipe.fixedQuality());assertFalse(recipe.eligible(scores[i]-1,4));assertTrue(recipe.eligible(scores[i],4));assertFalse(recipe.eligible(100,qualities[i]-1));assertEquals(3,recipe.ingredients().size());}
        assertEquals(r.recipe("zhuji_low").orElseThrow().ingredients(),r.recipe("zhuji_middle").orElseThrow().ingredients());
        assertNotEquals(r.recipe("zhuji_middle").orElseThrow().ingredients().getFirst(),r.recipe("zhuji_high").orElseThrow().ingredients().getFirst());
        assertTrue(r.recipe("buji_high").orElseThrow().count(0)>r.recipe("zhuji_high").orElseThrow().count(0));
    }
}
