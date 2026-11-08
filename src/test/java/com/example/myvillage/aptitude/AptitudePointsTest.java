package com.example.myvillage.aptitude;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AptitudePointsTest {
    @Test void elementPointsAreIndependentAndCanExceedOneHundred() {
        var fire=ResourceLocation.parse("myvillage:fire"); var water=ResourceLocation.parse("myvillage:water");
        var root=new SpiritualRoot(Map.of(fire,300,water,200));
        assertEquals(200,root.with(fire,315).points().get(water));
        assertEquals(315,root.with(fire,315).points().get(fire));
        assertEquals(root,SpiritualRoot.CODEC.parse(JsonOps.INSTANCE,SpiritualRoot.CODEC.encodeStart(JsonOps.INSTANCE,root).getOrThrow()).getOrThrow());
    }
    @Test void craftingStateAndQualityAcceptAdvancedPoints() {
        var values=new CraftingAptitudes(CraftingAptitudes.CURRENT_VERSION,true,300,250,200);
        assertEquals(4,values.qualityCap(CraftingAptitudes.Profession.ALCHEMY,CraftingRules.get()));
        var next=values.withScore(CraftingAptitudes.Profession.FORGING,400);
        assertEquals(300,next.alchemy()); assertEquals(200,next.talisman()); assertEquals(400,next.forging());
        assertEquals(next,CraftingAptitudes.CODEC.parse(JsonOps.INSTANCE,CraftingAptitudes.CODEC.encodeStart(JsonOps.INSTANCE,next).getOrThrow()).getOrThrow());
    }
    @Test void weaponGenerationUsesStableIndependentDomains() {
        var id=new UUID(10,20); var first=AptitudeValues.generate(42,id,0,false);
        assertEquals(first,AptitudeValues.generate(42,id,0,false));
        assertEquals(java.util.Set.copyOf(AptitudeRules.get().weapons()),first.values().keySet());
        assertNotEquals(first,AptitudeValues.generate(43,id,0,false));
        assertEquals(first.get("spear"),first.with("sword",350).get("spear"));
    }
    @Test void savedGrowthAndTrainingRoundTripTogether() {
        var course=new AptitudeTraining("research","forging","craft",0,1,15,1000,"minecraft:overworld",-7,64,11,-1);
        var state=new AptitudeState(true,new AptitudeValues(Map.of("sword",300,"spear",200,"fist",100)),
                Map.of("sword",19L),Map.of("land/fire",900L),Optional.of(course));
        assertEquals(state,AptitudeState.CODEC.parse(JsonOps.INSTANCE,AptitudeState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow()).getOrThrow());
        assertEquals(1,course.advance(Integer.MAX_VALUE).progress());
    }
    @Test void invalidValuesAndInvalidTrainingAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new AptitudeValues(Map.of("sword",-1)));
        assertThrows(IllegalArgumentException.class,()->new AptitudeValues(Map.of("unknown",1)));
        assertThrows(IllegalArgumentException.class,()->new AptitudeTraining("research","forging","craft",0,0,15,1000,"minecraft:overworld",0,0,0,-1));
        assertThrows(IllegalArgumentException.class,()->new AptitudeState(false,new AptitudeValues(Map.of("sword",1)),Map.of(),Map.of(),Optional.empty()));
    }
    @Test void damageCurveKeepsGrowingWithoutAnAttributeCap() {
        var r=AptitudeRules.get(); assertEquals(1,r.multiplier(0));
        assertTrue(r.multiplier(300)>r.multiplier(100));
        assertTrue(r.multiplier(1000)>r.multiplier(300));
        assertTrue(r.multiplier(Integer.MAX_VALUE)<1+r.maximumBonus());
        assertThrows(IllegalArgumentException.class,()->r.multiplier(-1));
    }
    @Test void pointAdditionIsSafeAtIntegerStorageBoundary() {
        assertEquals(350,AptitudeRules.add(300,50));
        assertEquals(Integer.MAX_VALUE,AptitudeRules.add(Integer.MAX_VALUE-1,10));
        assertThrows(IllegalArgumentException.class,()->AptitudeRules.add(0,-1));
    }
    @Test void confirmedFateRemainsActiveAfterDevelopingAnotherRoot() {
        var fire = ResourceLocation.parse("myvillage:fire");
        var water = ResourceLocation.parse("myvillage:water");
        var fate = new com.example.myvillage.cultivation.fate.FateDefinition("test.name", "test.description",
                0, 1, "test", true, Map.of(), Optional.of(fire), 90, 1);
        var initial = Optional.of(new SpiritualRoot(Map.of(fire,100)));
        var developed = Optional.of(new SpiritualRoot(Map.of(fire,300,water,200)));
        assertTrue(fate.eligible(initial));
        assertFalse(fate.eligible(developed));
        assertTrue(fate.active(developed));
        assertFalse(fate.active(Optional.empty()));
    }
}
