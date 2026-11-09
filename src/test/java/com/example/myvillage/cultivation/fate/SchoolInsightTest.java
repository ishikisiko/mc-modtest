package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.network.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchoolInsightTest {
    @BeforeAll static void bootstrap(){
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private static FateDefinition definition()throws Exception{
        try(var in=SchoolInsightTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/school_insight.json")){
            assertNotNull(in);return FateDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8))).getOrThrow();
        }
    }
    private static TechniqueDefinition technique(String id)throws Exception{
        try(var in=SchoolInsightTest.class.getResourceAsStream("/data/myvillage/myvillage/technique/"+id+".json")){
            assertNotNull(in);return TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8))).getOrThrow();
        }
    }
    @Test void choiceIsRequiredForThisFateAndMustNameAnAvailableSchool()throws Exception{
        var d=definition();assertEquals(2,d.tier());assertEquals(3,d.cost());assertEquals(.4,d.effect("school_study"));assertEquals(.4,d.effect("school_mastery"));
        var definitions=Map.of("myvillage:school_insight",d);var picks=List.of("myvillage:school_insight");var available=Set.of("myvillage:fire_arts","myvillage:sword");
        assertThrows(IllegalArgumentException.class,()->SchoolInsight.confirm(picks,definitions,"",available));
        assertThrows(IllegalArgumentException.class,()->SchoolInsight.confirm(picks,definitions,"other:school",available));
        assertEquals(new SchoolInsightState("myvillage:fire_arts"),SchoolInsight.confirm(picks,definitions,"myvillage:fire_arts",available));
        assertEquals(SchoolInsightState.EMPTY,SchoolInsight.confirm(List.of(),definitions,"",available));
        assertThrows(IllegalArgumentException.class,()->SchoolInsight.confirm(List.of(),definitions,"myvillage:sword",available));
        assertThrows(IllegalArgumentException.class,()->new SchoolInsightState("invalid id"));
    }
    @Test void matchingUsesTheDeclaredSchoolRatherThanElementOrWeaponName()throws Exception{
        var fire=new SchoolInsightState("myvillage:fire_arts");
        assertTrue(SchoolInsight.matches(fire,technique("linghuo_shu")));
        assertFalse(SchoolInsight.matches(fire,technique("shuiren_shu")));
        assertFalse(SchoolInsight.matches(fire,technique("basic_breathing")));
        assertFalse(SchoolInsight.matches(SchoolInsightState.EMPTY,technique("linghuo_shu")));
    }
    @Test void FiveOnePointRewardsBecomeSevenAndFractionSurvivesSerialization(){
        var p=TechniqueProgress.ZERO;
        p=SchoolInsight.gain(p,1,4000,100);assertEquals(1,p.masteryPoints());assertEquals(4000,p.masteryRemainder());
        p=TechniqueProgress.CODEC.parse(JsonOps.INSTANCE,TechniqueProgress.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow()).getOrThrow();
        for(int i=1;i<5;i++)p=SchoolInsight.gain(p,1,4000,100);
        assertEquals(7,p.masteryPoints());assertEquals(0,p.masteryRemainder());
        assertEquals(14,SchoolInsight.gain(TechniqueProgress.ZERO,10,4000,100).masteryPoints());
    }
    @Test void GainHonorsCapsAndNeverOverflowsOrChangesInsight(){
        var p=new TechniqueProgress(9,"flow",2,7,8000);var gain=SchoolInsight.gain(p,1,4000,10);
        assertEquals(new TechniqueProgress(10,"flow",2,7,0),gain);
        assertEquals(Long.MAX_VALUE,SchoolInsight.gain(TechniqueProgress.ZERO,Long.MAX_VALUE,4000,Long.MAX_VALUE).masteryPoints());
        assertSame(p,SchoolInsight.gain(p,0,4000,100));
    }
    @Test void PayloadsCarryBoundedChoiceAndSavedFraction(){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
        try{
            var intent=new FateIntentPayload((byte)2,17,(byte)7,22,"myvillage:fire_arts");FateIntentPayload.STREAM_CODEC.encode(buffer,intent);
            assertEquals(intent,FateIntentPayload.STREAM_CODEC.decode(buffer));
            var state=new SchoolInsightState("myvillage:fire_arts");
            var profile=CultivationProfile.defaultProfile().learnTechnique(ResourceLocation.parse("test:move"))
                    .withTechniqueProgress(ResourceLocation.parse("test:move"),new TechniqueProgress(1,"",0,0,4000));
            var snapshot=new CultivationSnapshotPayload(profile,FateState.empty(),8,com.example.myvillage.cultivation.crafting.CraftingAptitudes.EMPTY,0,0,0,EverlastingState.EMPTY,com.example.myvillage.aptitude.AptitudeState.EMPTY,state);
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer,snapshot);assertEquals(snapshot,CultivationSnapshotPayload.STREAM_CODEC.decode(buffer));
            assertThrows(IllegalArgumentException.class,()->new FateIntentPayload((byte)2,1,(byte)7,3,"a".repeat(129)));
        }finally{buffer.release();}
    }
}
